package com.example.agentgateway.infrastructure.filteredstream.client;

import com.example.agentgateway.application.filteredstream.UpstreamFrameException;
import com.example.agentgateway.application.filteredstream.model.UpstreamStreamFrame;
import com.example.agentgateway.domain.filteredstream.CardDefinition;
import com.example.agentgateway.domain.filteredstream.FallbackMessage;
import com.example.agentgateway.domain.filteredstream.FundCardSource;
import com.example.agentgateway.domain.filteredstream.FundDefinition;
import com.example.agentgateway.domain.filteredstream.LinkDefinition;
import com.example.agentgateway.domain.filteredstream.MetadataStatus;
import com.example.agentgateway.domain.filteredstream.OverRiskInfo;
import com.example.agentgateway.domain.filteredstream.ProcessedCardMapping;
import com.example.agentgateway.domain.filteredstream.ProcessedLinkMapping;
import com.example.agentgateway.domain.filteredstream.ProductRiskInfo;
import com.example.agentgateway.domain.filteredstream.StreamMetadata;
import com.example.agentgateway.domain.filteredstream.SuggestedQueryDefinition;
import com.example.agentgateway.domain.filteredstream.ThemeRiskFundInfo;
import com.example.agentgateway.domain.filteredstream.ThemeRiskTipInfo;
import com.example.agentgateway.domain.filteredstream.ThemeRiskUserInfo;
import com.example.agentgateway.domain.filteredstream.UserRiskInfo;
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
 * <p>只接受外部 API 的 status + content 帧协议；不符合协议的非空帧
 * 会抛出异常，由应用层转换为兜底输出。</p>
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
        // WebClient 已拆出单个 SSE data；这里仍兼容直接传入 data: 前缀。
        if (rawFrame == null) {
            return UpstreamStreamFrame.skip();
        }
        String payload = removeDataPrefix(rawFrame).trim();
        if (payload.isEmpty()) {
            return UpstreamStreamFrame.skip();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (IOException ex) {
            throw new UpstreamFrameException("上游流式帧不是合法 JSON 对象", ex);
        }
        if (root == null || !root.isObject()) {
            throw new UpstreamFrameException("上游流式帧必须是 JSON 对象");
        }

        String status = textAt(root, "status");
        if (isBlank(status)) {
            throw new UpstreamFrameException("上游流式帧缺少 status 字段");
        }
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
        if ("MIDDLE".equalsIgnoreCase(status)) {
            return contentFrame(root, false);
        }
        if ("END".equalsIgnoreCase(status)) {
            return contentFrame(root, true);
        }
        throw new UpstreamFrameException("不支持的上游流式帧 status: " + status);
    }

    private UpstreamStreamFrame contentFrame(JsonNode root, boolean end) {
        JsonNode content = root.path("content");
        if (end && (content.isMissingNode() || content.isNull())) {
            return UpstreamStreamFrame.done();
        }
        if (!content.isTextual()) {
            throw new UpstreamFrameException("上游 " + (end ? "END" : "MIDDLE")
                    + " 帧缺少字符串类型的 content 字段");
        }
        // END 帧若带最后一段增量，仍先将 content 交给正文处理，再结束上游。
        return UpstreamStreamFrame.content(content.asText(), end);
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
        String errorMsg = firstText(metadataBody.path("errorMsg"));
        List<FallbackMessage> fallbackAnswer =
                extractFallbackAnswer(metadataBody);
        if (status == MetadataStatus.FALLBACK
                || status == MetadataStatus.REFUSE) {
            // 这两种首帧状态都不会继续处理正文；无关业务字段的校验
            // 不能阻断终止指令，原始 Metadata body 仍交给接口层输出。
            return StreamMetadata.builder()
                    .linkMapping(new ArrayList<LinkDefinition>())
                    .cardMapping(new ArrayList<CardDefinition>())
                    .fundList(FundCardSource.empty())
                    .fallbackAnswer(fallbackAnswer)
                    .status(status)
                    .errorMsg(errorMsg)
                    .build();
        }
        return StreamMetadata.builder()
                .linkMapping(extractLinks(metadataBody))
                .cardMapping(extractCards(metadataBody))
                .fundList(extractFundList(metadataBody))
                .overRiskInfo(extractOverRiskInfo(metadataBody))
                .themeRiskTipInfo(extractThemeRiskTipInfo(metadataBody))
                .suggestedQuery(extractSuggestedQueries(metadataBody))
                .fallbackAnswer(fallbackAnswer)
                .status(MetadataStatus.NORMAL)
                .errorMsg(errorMsg)
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
                    "Metadata.status 必须为 NORMAL、FALLBACK 或 REFUSE");
        }
        String value = statusNode.asText();
        try {
            return MetadataStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new UpstreamFrameException(
                    "Metadata.status 必须为 NORMAL、FALLBACK 或 REFUSE, 实际值=" + value,
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

    /** 按首帧 fundList 原有的对象层级解析，避免在处理链中拆成平行字段。 */
    private FundCardSource extractFundList(JsonNode metadataBody) {
        JsonNode fundList = metadataBody.path("fundList");
        if (fundList.isMissingNode() || fundList.isNull()) {
            return FundCardSource.empty();
        }
        if (!fundList.isObject()) {
            throw new UpstreamFrameException("Metadata.fundList 必须是对象");
        }
        JsonNode subtitle = fundList.path("subtitle");
        if (!subtitle.isMissingNode() && !subtitle.isNull()
                && !subtitle.isTextual()) {
            throw new UpstreamFrameException("Metadata.fundList.subtitle 必须是字符串");
        }
        List<FundDefinition> result = new ArrayList<FundDefinition>();
        JsonNode list = fundList.path("list");
        if (!list.isArray()) {
            throw new UpstreamFrameException("Metadata.fundList.list 必须是数组");
        }
        for (JsonNode item : list) {
            if (!item.isObject()) {
                throw new UpstreamFrameException(
                        "Metadata.fundList.list 元素必须是对象");
            }
            result.add(FundDefinition.builder()
                    .fundId(firstText(item.path("fundId")))
                    .fundName(firstText(item.path("fundName")))
                    .fundDesc(firstText(item.path("fundDesc")))
                    .build());
        }
        return FundCardSource.builder()
                .subtitle(subtitle.isMissingNode() || subtitle.isNull()
                        ? "" : subtitle.asText())
                .list(result)
                .build();
    }

    /** 与 linkMapping/cardMapping 一样，只在首帧解析一次风险信息。 */
    private OverRiskInfo extractOverRiskInfo(JsonNode metadataBody) {
        JsonNode risk = metadataBody.path("overRiskInfo");
        if (risk.isMissingNode() || risk.isNull()) {
            return null;
        }
        if (!risk.isObject()) {
            throw new UpstreamFrameException("Metadata.overRiskInfo 必须是对象");
        }
        JsonNode user = risk.path("userRiskInfo");
        if (!user.isMissingNode() && !user.isNull() && !user.isObject()) {
            throw new UpstreamFrameException(
                    "Metadata.overRiskInfo.userRiskInfo 必须是对象");
        }
        UserRiskInfo userRiskInfo = user.isObject()
                ? UserRiskInfo.builder()
                        .userIsMinor(firstText(user.path("userIsMinor")))
                        .userRiskLevel(firstText(user.path("userRiskLevel")))
                        .userRiskStatus(firstText(user.path("userRiskStatus")))
                        .build()
                : null;
        JsonNode products = risk.path("productRiskInfo");
        if (!products.isArray()) {
            throw new UpstreamFrameException(
                    "Metadata.overRiskInfo.productRiskInfo 必须是数组");
        }
        List<ProductRiskInfo> productRiskInfo =
                new ArrayList<ProductRiskInfo>();
        for (JsonNode product : products) {
            if (!product.isObject()) {
                throw new UpstreamFrameException(
                        "Metadata.overRiskInfo.productRiskInfo 元素必须是对象");
            }
            String code = firstText(product.path("productCode"));
            if (isBlank(code)) {
                throw new UpstreamFrameException(
                        "Metadata.overRiskInfo.productRiskInfo.productCode 不能为空");
            }
            productRiskInfo.add(ProductRiskInfo.builder()
                    .productName(firstText(product.path("productName")))
                    .productCode(code)
                    .bscRskLvl(firstText(product.path("bscRskLvl")))
                    .build());
        }
        return OverRiskInfo.builder()
                .userRiskInfo(userRiskInfo)
                .productRiskInfo(Collections.unmodifiableList(productRiskInfo))
                .build();
    }

    /** themeRiskTipInfo 只在首帧解析一次，保留原始基金 ID 字符串中的前导零。 */
    private ThemeRiskTipInfo extractThemeRiskTipInfo(JsonNode metadataBody) {
        JsonNode risk = metadataBody.path("themeRiskTipInfo");
        if (risk.isMissingNode() || risk.isNull()) {
            return null;
        }
        if (!risk.isObject()) {
            throw new UpstreamFrameException(
                    "Metadata.themeRiskTipInfo 必须是对象");
        }
        JsonNode user = risk.path("userInfo");
        if (!user.isObject()) {
            throw new UpstreamFrameException(
                    "Metadata.themeRiskTipInfo.userInfo 必须是对象");
        }
        ThemeRiskUserInfo userInfo = ThemeRiskUserInfo.builder()
                .riskLevel(firstText(user.path("riskLevel")))
                .isPrivate(user.path("isPrivate").isBoolean()
                        ? user.path("isPrivate").booleanValue() : null)
                .valueStyleStatus(firstText(user.path("valueStyleStatus")))
                .balanceStyleStatus(firstText(user.path("balanceStyleStatus")))
                .growthStyleStatus(firstText(user.path("growthStyleStatus")))
                .build();
        JsonNode funds = risk.path("fundList");
        if (!funds.isArray()) {
            throw new UpstreamFrameException(
                    "Metadata.themeRiskTipInfo.fundList 必须是数组");
        }
        List<ThemeRiskFundInfo> fundList = new ArrayList<ThemeRiskFundInfo>();
        for (JsonNode fund : funds) {
            if (!fund.isObject() || isBlank(firstText(fund.path("fundId")))) {
                throw new UpstreamFrameException(
                        "Metadata.themeRiskTipInfo.fundList.fundId 不能为空");
            }
            fundList.add(ThemeRiskFundInfo.builder()
                    .fundId(firstText(fund.path("fundId")))
                    .riskLevel(firstText(fund.path("riskLevel")))
                    .fundType(firstText(fund.path("fundType")))
                    .build());
        }
        return ThemeRiskTipInfo.builder()
                .userInfo(userInfo)
                .fundList(Collections.unmodifiableList(fundList))
                .build();
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
