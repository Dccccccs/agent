package com.example.agentgateway.infrastructure.filteredstream.client;

import com.example.agentgateway.application.filteredstream.UpstreamFrameException;
import com.example.agentgateway.application.filteredstream.model.UpstreamStreamFrame;
import com.example.agentgateway.domain.filteredstream.CardDefinition;
import com.example.agentgateway.domain.filteredstream.FallbackMessage;
import com.example.agentgateway.domain.filteredstream.FundDefinition;
import com.example.agentgateway.domain.filteredstream.LinkDefinition;
import com.example.agentgateway.domain.filteredstream.MetadataStatus;
import com.example.agentgateway.domain.filteredstream.ProcessedCardMapping;
import com.example.agentgateway.domain.filteredstream.ProcessedLinkMapping;
import com.example.agentgateway.domain.filteredstream.StreamMetadata;
import com.example.agentgateway.domain.filteredstream.SuggestedQueryDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 将外部 API 的单个 SSE data 解码为应用层统一帧，是上游协议适配的核心位置。
 *
 * <p>兼容纯文本、项目约定的 returnType 帧、模型实际返回的 status 帧以及
 * OpenAI 风格 choices 增量。协议差异在此层被消化，应用层只处理统一模型。</p>
 *
 * <p>当前外部模型的典型原始帧如下：</p>
 * <pre>
 * 首帧：{"label":"构建源数据","status":"METADATA",
 *        "content":"{\"status\":\"NORMAL\",...}"}
 * 中间帧：{"status":"MIDDLE","content":"本帧增量文本"}
 * 尾帧：{"status":"END","content":"最后一段增量"}
 * 错误帧：{"status":"ERROR","content":"错误原因"}
 * </pre>
 */
@Component
@Slf4j
public class UpstreamStreamFrameConverter {
    private final ObjectMapper objectMapper;

    public UpstreamStreamFrameConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public UpstreamStreamFrame convert(String rawFrame) {
        // rawFrame 是一个 SSE data 的完整内容，不是截至当前的全量回答。
        // 方法每调用一次只处理一帧，并返回一个内部统一帧。
        if (rawFrame == null) {
            return UpstreamStreamFrame.skip();
        }
        String payload = removeDataPrefix(rawFrame);
        String trimmed = payload.trim();
        if (trimmed.isEmpty()) {
            return UpstreamStreamFrame.skip();
        }
        if ("[DONE]".equals(trimmed)) {
            return UpstreamStreamFrame.done();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(trimmed);
        } catch (IOException ignored) {
            // JSON 解析失败不一定是错误：上游允许直接把普通文本放进 SSE data。
            return UpstreamStreamFrame.content(payload);
        }
        if (root == null) {
            return UpstreamStreamFrame.skip();
        }
        if (root.isTextual()) {
            return UpstreamStreamFrame.content(root.asText());
        }

        // 分支一：兼容本系统文档格式，例如
        // {"returnType":"DATA","body":{"answer":[...]}}。
        String returnType = textAt(root, "returnType");
        // 先兼容本接口文档约定的 METADATA/DATA/ERROR/DONE 协议。
        if ("DONE".equalsIgnoreCase(returnType)) {
            return UpstreamStreamFrame.done();
        }
        if ("ERROR".equalsIgnoreCase(returnType)) {
            String message = textAt(root, "message");
            throw new UpstreamFrameException(
                    isBlank(message) ? "上游流式API返回错误帧" : message);
        }
        if ("METADATA".equalsIgnoreCase(returnType)
                || isMetadataDataFrame(root)) {
            JsonNode body = root.path("body");
            return buildMetadataFrame(body);
        }

        // 分支二：处理外部模型真实格式，即 status + content。
        String status = textAt(root, "status");
        // 再处理外部模型实际使用的 METADATA/MIDDLE/END/ERROR 协议。
        if ("METADATA".equalsIgnoreCase(status)) {
            return normalizeMetadataFrame(root);
        }
        if ("ERROR".equalsIgnoreCase(status)) {
            String message = firstText(
                    root.path("message"),
                    root.path("errorMsg"),
                    root.path("content"));
            throw new UpstreamFrameException(
                    isBlank(message) ? "上游流式API返回错误帧" : message);
        }
        boolean terminalAfterContent = "END".equalsIgnoreCase(status)
                || "DONE".equalsIgnoreCase(status);

        // 外部模型的 MIDDLE 帧必须直接携带字符串类型的 content。这里不能继续
        // 落入“未知 JSON 按原文输出”的兼容分支，否则协议异常会污染最终答案。
        // END/DONE 帧仍允许不携带 content，表示没有最后一段增量、直接结束。
        if ("MIDDLE".equalsIgnoreCase(status)) {
            JsonNode middleContent = root.path("content");
            if (!middleContent.isTextual()) {
                throw new UpstreamFrameException(
                        "上游 MIDDLE 帧缺少字符串类型的 content 字段");
            }
        }

        String content = firstText(
                root.path("content"),
                root.path("data").path("content"),
                root.path("body").path("answer").path(0).path("data").path("content"),
                root.path("choices").path(0).path("delta").path("content"),
                root.path("choices").path(0).path("text"),
                root.path("message").path("content"),
                root.path("answer"));
        if (content != null) {
            // END 帧可能同时携带最后一段增量，不能因为 done=true 而丢弃 content。
            return UpstreamStreamFrame.content(content, terminalAfterContent);
        }
        if (terminalAfterContent) {
            return UpstreamStreamFrame.done();
        }
        // 未知 JSON 不能静默丢失，按原始文本交给领域处理器，便于发现协议变化。
        return UpstreamStreamFrame.content(payload);
    }

    private String removeDataPrefix(String value) {
        if (!value.startsWith("data:")) {
            return value;
        }
        String result = value.substring("data:".length());
        return result.startsWith(" ") ? result.substring(1) : result;
    }

    private String firstText(JsonNode... candidates) {
        for (JsonNode candidate : candidates) {
            if (candidate != null && candidate.isTextual()) {
                return candidate.asText();
            }
        }
        return null;
    }

    private String textAt(JsonNode root, String field) {
        JsonNode value = root.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private UpstreamStreamFrame normalizeMetadataFrame(JsonNode root) {
        // 外部首帧：{"status":"METADATA","content":"{元数据JSON字符串}"}
        // 下游首帧：{"body":{元数据对象},"returnType":"METADATA"}
        // 这里只改造外包装；content 对象内的所有字段均原样放入 body。
        JsonNode content = root.path("content");
        JsonNode metadataBody;
        if (content.isTextual()) {
            try {
                metadataBody = objectMapper.readTree(content.asText());
            } catch (IOException ex) {
                throw new UpstreamFrameException(
                        "上游元数据帧的 content 不是合法 JSON 对象", ex);
            }
        } else {
            metadataBody = content;
        }
        if (metadataBody == null || !metadataBody.isObject()) {
            throw new UpstreamFrameException(
                    "上游元数据帧的 content 必须是 JSON 对象");
        }
        return buildMetadataFrame(metadataBody);
    }

    private UpstreamStreamFrame buildMetadataFrame(JsonNode metadataBody) {
        if (metadataBody == null || !metadataBody.isObject()) {
            throw new UpstreamFrameException("Metadata.body 必须是 JSON 对象");
        }
        ObjectNode normalized = objectMapper.createObjectNode();
        // 最终首帧只包含目标接口协议字段，不透传外部 label/status/content
        // 包装层；metadataBody 内的扩展字段则全部保留。
        normalized.set("body", metadataBody.deepCopy());
        normalized.put("returnType", "METADATA");
        return UpstreamStreamFrame.metadata(normalized.toString(),
                extractMetadata(metadataBody));
    }

    private StreamMetadata extractMetadata(JsonNode metadataBody) {
        // metadataJson 负责构建最终 JSON 对象；StreamMetadata 只抽取后续规则
        // 真正需要的业务字段，二者不能相互替代。fallbackAnswer 解析失败
        // 不阻断主流，而是由应用层回退到配置兜底话术。
        MetadataStatus status = extractMetadataStatus(metadataBody);
        List<FallbackMessage> fallbackAnswer =
                extractFallbackAnswer(metadataBody);
        if (status == MetadataStatus.FALLBACK) {
            // 主动兜底首帧后不会再处理正文，因而不让无关的业务字段
            // 校验阻断 FALLBACK 指令；原始 body 仍会完整向下游返回。
            return StreamMetadata.builder()
                    .linkMapping(new ArrayList<LinkDefinition>())
                    .cardMapping(new ArrayList<CardDefinition>())
                    .fundList(new ArrayList<FundDefinition>())
                    .fallbackAnswer(fallbackAnswer)
                    .status(MetadataStatus.FALLBACK)
                    .build();
        }
        return StreamMetadata.builder()
                .linkMapping(extractLinks(metadataBody))
                .cardMapping(extractCards(metadataBody))
                .fundList(extractFunds(metadataBody))
                .suggestedQuery(extractSuggestedQueries(metadataBody))
                .fallbackAnswer(fallbackAnswer)
                .status(MetadataStatus.NORMAL)
                .build();
    }

    /**
     * 解析首帧中的 fallbackAnswer。兜底答案本身即使格式错误，也不能成为
     * 新的流式失败来源，因此无效值只记录告警并返回 null，交给应用层使用
     * 配置的默认兜底话术。
     */
    private List<FallbackMessage> extractFallbackAnswer(JsonNode metadataBody) {
        JsonNode answers = metadataBody.path("fallbackAnswer");
        if (answers.isMissingNode() || answers.isNull()
                || answers.isArray() && answers.size() == 0) {
            return Collections.emptyList();
        }
        try {
            if (!answers.isArray()) {
                throw new IllegalArgumentException(
                        "fallbackAnswer 必须是数组");
            }
            List<FallbackMessage> result =
                    new ArrayList<FallbackMessage>();
            for (JsonNode answer : answers) {
                if (!answer.isObject()) {
                    throw new IllegalArgumentException(
                            "fallbackAnswer 元素必须是对象");
                }
                String type = firstText(answer.path("type"));
                if (!"text".equals(type)) {
                    throw new IllegalArgumentException(
                            "fallbackAnswer.type 必须为 text");
                }
                String content = firstText(answer.path("content"));
                if (isBlank(content)) {
                    throw new IllegalArgumentException(
                            "fallbackAnswer.content 不能为空");
                }
                result.add(FallbackMessage.builder()
                        .content(content)
                        .linkMapping(extractFallbackLinks(answer))
                        .cardMapping(extractFallbackCards(answer))
                        .build());
            }
            return Collections.unmodifiableList(result);
        } catch (RuntimeException ex) {
            log.warn("Metadata.fallbackAnswer 格式无效，将使用配置兜底话术: {}",
                    ex.getMessage());
            // 任意答案块无效时放弃整组，避免向客户端下发部分兜底答案。
            return Collections.emptyList();
        }
    }

    private List<ProcessedLinkMapping> extractFallbackLinks(JsonNode answer) {
        List<ProcessedLinkMapping> result =
                new ArrayList<ProcessedLinkMapping>();
        JsonNode mappings = answer.path("linkMapping");
        if (mappings.isMissingNode() || mappings.isNull()) {
            return result;
        }
        if (!mappings.isArray()) {
            throw new IllegalArgumentException(
                    "fallbackAnswer.linkMapping 必须是数组");
        }
        for (JsonNode item : mappings) {
            if (!item.isObject()) {
                throw new IllegalArgumentException(
                        "fallbackAnswer.linkMapping 元素必须是对象");
            }
            String linkId = firstText(item.path("linkId"));
            String linkContent = firstText(item.path("linkContent"));
            if (isBlank(linkId) || isBlank(linkContent)) {
                throw new IllegalArgumentException(
                        "fallbackAnswer.linkMapping 字段不能为空");
            }
            result.add(ProcessedLinkMapping.builder()
                    .linkId(linkId)
                    .linkContent(linkContent)
                    .build());
        }
        return result;
    }

    private List<ProcessedCardMapping> extractFallbackCards(JsonNode answer) {
        List<ProcessedCardMapping> result =
                new ArrayList<ProcessedCardMapping>();
        JsonNode mappings = answer.path("cardMapping");
        if (mappings.isMissingNode() || mappings.isNull()) {
            return result;
        }
        if (!mappings.isArray()) {
            throw new IllegalArgumentException(
                    "fallbackAnswer.cardMapping 必须是数组");
        }
        for (JsonNode item : mappings) {
            if (!item.isObject()) {
                throw new IllegalArgumentException(
                        "fallbackAnswer.cardMapping 元素必须是对象");
            }
            String cardId = firstText(item.path("cardId"));
            String htmlContent = firstText(item.path("htmlContent"));
            if (isBlank(cardId) || htmlContent == null) {
                throw new IllegalArgumentException(
                        "fallbackAnswer.cardMapping 字段不能为空");
            }
            result.add(ProcessedCardMapping.builder()
                    .cardId(cardId)
                    .htmlContent(htmlContent)
                    .build());
        }
        return result;
    }

    /**
     * 首帧推荐问暂存在强类型 Metadata 中。接口首帧会删除该字段，应用层在
     * 流结束时将这里的值与动态基金推荐问合并后统一输出。
     */
    private List<SuggestedQueryDefinition> extractSuggestedQueries(
            JsonNode metadataBody) {
        List<SuggestedQueryDefinition> result =
                new ArrayList<SuggestedQueryDefinition>();
        JsonNode suggestions = metadataBody.path("suggestedQuery");
        if (suggestions.isMissingNode() || suggestions.isNull()) {
            return result;
        }
        if (!suggestions.isArray()) {
            throw new UpstreamFrameException(
                    "Metadata.suggestedQuery 必须是数组");
        }
        for (JsonNode item : suggestions) {
            if (!item.isObject()) {
                throw new UpstreamFrameException(
                        "Metadata.suggestedQuery 元素必须是对象");
            }
            String query = firstText(item.path("query"));
            if (isBlank(query)) {
                throw new UpstreamFrameException(
                        "Metadata.suggestedQuery.query 不能为空");
            }
            JsonNode intentNode = item.get("intent");
            if (intentNode != null && !intentNode.isNull()
                    && !intentNode.isTextual()) {
                throw new UpstreamFrameException(
                        "Metadata.suggestedQuery.intent 必须是字符串或 null");
            }
            result.add(SuggestedQueryDefinition.builder()
                    .query(query)
                    .intent(intentNode == null || intentNode.isNull()
                            ? null : intentNode.asText())
                    .build());
        }
        return result;
    }

    private MetadataStatus extractMetadataStatus(JsonNode metadataBody) {
        JsonNode statusNode = metadataBody == null
                ? null : metadataBody.get("status");
        if (statusNode == null || !statusNode.isTextual()) {
            throw new UpstreamFrameException(
                    "Metadata.status 必须为 NORMAL 或 FALLBACK");
        }
        String value = statusNode.asText();
        try {
            return MetadataStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new UpstreamFrameException(
                    "Metadata.status 必须为 NORMAL 或 FALLBACK, 实际值=" + value,
                    ex);
        }
    }

    /**
     * 首帧 linkMapping 是链接对象数组，业务校验只读取每项的
     * linkId 和 linkContent，其他字段仍保留在原始 Metadata body 中。
     */
    private List<LinkDefinition> extractLinks(JsonNode metadataBody) {
        // 以强类型 List 保留首帧对象结构，不将业务数据转换为 Map。
        List<LinkDefinition> result = new ArrayList<LinkDefinition>();
        if (metadataBody == null || !metadataBody.isObject()) {
            return result;
        }
        JsonNode linkMapping = metadataBody.path("linkMapping");
        if (linkMapping.isMissingNode() || linkMapping.isNull()) {
            return result;
        }
        if (!linkMapping.isArray()) {
            throw new UpstreamFrameException(
                    "Metadata.linkMapping 必须是数组");
        }
        for (JsonNode item : linkMapping) {
            if (!item.isObject()) {
                throw new UpstreamFrameException(
                        "Metadata.linkMapping 元素必须是对象");
            }
            addLink(result, firstText(item.path("linkId")),
                    firstText(item.path("linkContent")));
        }
        return result;
    }

    private void addLink(List<LinkDefinition> target,
                         String linkId,
                         String linkContent) {
        // 即使必填字段缺失也保留该项，后续领域校验会将
        // 不完整 Metadata 转换为兜底帧，而不是静默忽略脏数据。
        target.add(LinkDefinition.builder()
                .linkId(linkId)
                .linkContent(linkContent == null ? "" : linkContent)
                .build());
    }

    /**
     * 将首帧 cardMapping 列表转换为占位符索引。cardPlaceHolder
     * 兼容 card_xxx 和 {card_xxx} 两种写法，内部统一保存为
     * 不带大括号的 card_xxx。
     */
    private List<CardDefinition> extractCards(JsonNode metadataBody) {
        List<CardDefinition> result = new ArrayList<CardDefinition>();
        if (metadataBody == null || !metadataBody.isObject()) {
            return result;
        }
        JsonNode cardMapping = metadataBody.path("cardMapping");
        if (cardMapping.isMissingNode() || cardMapping.isNull()) {
            return result;
        }
        if (!cardMapping.isArray()) {
            throw new UpstreamFrameException(
                    "Metadata.cardMapping 必须是数组");
        }
        for (JsonNode item : cardMapping) {
            if (!item.isObject()) {
                throw new UpstreamFrameException(
                        "Metadata.cardMapping 元素必须是对象");
            }
            String placeholder = normalizeCardPlaceholder(
                    firstText(item.path("cardPlaceHolder")));
            if (isBlank(placeholder)) {
                throw new UpstreamFrameException(
                        "Metadata.cardMapping.cardPlaceHolder 不能为空");
            }
            if (containsCardPlaceholder(result, placeholder)) {
                throw new UpstreamFrameException(
                        "Metadata.cardMapping 存在重复占位符: "
                                + placeholder);
            }
            result.add(CardDefinition.builder()
                    .cardPlaceHolder(placeholder)
                    .cardId(firstText(item.path("cardId")))
                    .cardName(firstText(item.path("cardName")))
                    .cardDescription(firstText(item.path("cardDescription")))
                    // 当前首帧协议没有 cardData，按卡片协议输出空 JSON。
                    .htmlContent(firstText(item.path("htmlContent")))
                    .build());
        }
        return result;
    }

    private boolean containsCardPlaceholder(List<CardDefinition> cards,
                                            String placeholder) {
        for (CardDefinition card : cards) {
            if (placeholder.equals(card.getCardPlaceHolder())) {
                return true;
            }
        }
        return false;
    }

    /** 将首帧 fundList 转为领域基金对象，字段完整性由领域层统一校验。 */
    private List<FundDefinition> extractFunds(JsonNode metadataBody) {
        List<FundDefinition> result = new ArrayList<FundDefinition>();
        if (metadataBody == null || !metadataBody.isObject()) {
            return result;
        }
        JsonNode fundList = metadataBody.path("fundList");
        if (fundList.isMissingNode() || fundList.isNull()) {
            return result;
        }
        if (!fundList.isArray()) {
            throw new UpstreamFrameException("Metadata.fundList 必须是数组");
        }
        for (JsonNode item : fundList) {
            if (!item.isObject()) {
                throw new UpstreamFrameException(
                        "Metadata.fundList 元素必须是对象");
            }
            result.add(FundDefinition.builder()
                    .fundId(firstText(item.path("fundId")))
                    .fundName(firstText(item.path("fundName")))
                    .fundDesc(firstText(item.path("fundDesc")))
                    .build());
        }
        return result;
    }

    private String normalizeCardPlaceholder(String placeholder) {
        if (placeholder == null) {
            return null;
        }
        String normalized = placeholder.trim();
        if (normalized.length() >= 2 && normalized.charAt(0) == '{'
                && normalized.charAt(normalized.length() - 1) == '}') {
            return normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private boolean isMetadataDataFrame(JsonNode root) {
        JsonNode body = root.path("body");
        JsonNode answer = body.path("answer");
        if (!body.isObject() || !answer.isArray() || answer.size() != 0) {
            return false;
        }
        return body.has("sections")
                || body.has("prompt")
                || body.has("riskTip")
                || body.has("agentAnswerSlots")
                || body.has("suggestedQuery")
                || body.has("recordId")
                || body.has("status")
                || body.has("linkMapping")
                || body.has("cardMapping")
                || body.has("fundList");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
