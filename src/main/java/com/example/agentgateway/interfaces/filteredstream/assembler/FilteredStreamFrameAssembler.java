package com.example.agentgateway.interfaces.filteredstream.assembler;

import com.example.agentgateway.application.filteredstream.model.FilteredStreamEvent;
import com.example.agentgateway.domain.filteredstream.FallbackMessage;
import com.example.agentgateway.domain.filteredstream.ProcessedContentBlock;
import com.example.agentgateway.interfaces.filteredstream.dto.FilteredStreamAnswerBlock;
import com.example.agentgateway.interfaces.filteredstream.dto.FilteredStreamFrame;
import com.example.agentgateway.interfaces.filteredstream.dto.FilteredStreamFrameBody;
import com.example.agentgateway.interfaces.filteredstream.dto.FilteredSuggestedQuery;
import com.example.agentgateway.interfaces.filteredstream.metadata.MetadataOutputCustomizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 接口层协议装配器。应用层只产生与传输协议无关的事件，本类负责将事件
 * 转换成对外约定的 METADATA、DATA、FALLBACK、DONE JSON 结构。
 */
@Component
public class FilteredStreamFrameAssembler {
    private final ObjectMapper objectMapper;
    private final List<MetadataOutputCustomizer> metadataCustomizers;
    private final FilteredStreamDtoAssembler dtoAssembler;

    public FilteredStreamFrameAssembler(
            ObjectMapper objectMapper,
            List<MetadataOutputCustomizer> metadataCustomizers,
            FilteredStreamDtoAssembler dtoAssembler) {
        this.objectMapper = objectMapper;
        this.metadataCustomizers = metadataCustomizers == null
                ? Collections.<MetadataOutputCustomizer>emptyList()
                : new ArrayList<MetadataOutputCustomizer>(metadataCustomizers);
        this.dtoAssembler = dtoAssembler;
    }

    public Object toPayload(FilteredStreamEvent event) {
        switch (event.getType()) {
            case METADATA:
                return toMetadataObject(event.getMetadataJson(),
                        event.getRecordId(), event.getFallbackMessages());
            case CONTENT:
                return toDataFrame(event.getContentBlock());
            case RECOMMENDATION:
                return toRecommendationFrame(event);
            case FALLBACK:
                return FilteredStreamFrame.fallback();
            case DONE:
                return FilteredStreamFrame.done();
            default:
                throw new IllegalArgumentException("未知流事件类型");
        }
    }

    private JsonNode toMetadataObject(String metadataJson,
                                      String recordId,
                                      List<FallbackMessage> fallbackMessages) {
        // Metadata 必须作为 JSON 对象交给 SseEmitter，不能直接返回 String；
        // 否则部分消息转换器会将整段 JSON 再次序列化成带引号的字符串。
        try {
            ObjectNode output;
            if (metadataJson == null || metadataJson.trim().isEmpty()) {
                output = objectMapper.createObjectNode();
                output.set("body", objectMapper.createObjectNode());
            } else {
                JsonNode metadata = objectMapper.readTree(metadataJson);
                if (metadata == null || !metadata.isObject()) {
                    throw new IllegalArgumentException(
                            "Metadata 首帧必须是 JSON 对象");
                }
                output = ((ObjectNode) metadata).deepCopy();
            }
            JsonNode body = output.path("body");
            if (!body.isObject()) {
                throw new IllegalArgumentException("Metadata.body 必须是 JSON 对象");
            }
            // 【最终首帧字段增删入口】定制器操作的是副本，不会污染首帧中
            // 供 URL、卡片等规则使用的强类型 StreamMetadata。
            for (MetadataOutputCustomizer customizer : metadataCustomizers) {
                customizer.customize((ObjectNode) body);
            }
            // 这些字段只供内部规则处理：链接、卡片和基金信息保留在强类型
            // StreamMetadata 中，推荐问延迟到 DONE 前的独立 METADATA 帧。
            // 删除发生在定制器之后，避免定制器意外将内部字段重新带回首帧。
            ((ObjectNode) body).remove(
                    Arrays.asList(
                            "linkMapping", "cardMapping", "fundList",
                            "suggestedQuery"));
            // 客户端在首帧即可拿到完整兜底回答；后续 FALLBACK 帧只发送
            // 类型通知，不再重复发送答案内容。
            ((ObjectNode) body).set("fallbackAnswer",
                    objectMapper.valueToTree(
                            dtoAssembler
                                    .assembleFallbackMetadataAnswerBlocks(
                                            fallbackMessages)));
            // recordId 不信任上游值，始终使用当前 AixzChatRequest.msgId。
            ((ObjectNode) body).put("recordId",
                    recordId == null ? "" : recordId);
            // 外层协议字段不开放给定制器，确保首帧类型始终正确。
            output.put("returnType", "METADATA");
            return output;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Metadata 首帧不是合法 JSON", ex);
        }
    }

    public FilteredStreamFrame toDataFrame(ProcessedContentBlock block) {
        FilteredStreamAnswerBlock answerBlock =
                dtoAssembler.assembleAnswerBlock(block);
        FilteredStreamFrameBody body = FilteredStreamFrameBody.builder()
                .answer(Collections.singletonList(answerBlock))
                .operate("append")
                .build();
        return FilteredStreamFrame.data(body);
    }

    private FilteredStreamFrame toRecommendationFrame(
            FilteredStreamEvent event) {
        // 推荐问是独立 METADATA 帧，只携带 suggestedQuery；应用服务保证
        // 它位于最后一段正文之后、纯 DONE 帧之前。
        List<FilteredSuggestedQuery> queries =
                dtoAssembler.assembleSuggestedQueries(
                        event.getRecommendationQueries());
        FilteredStreamFrameBody body = FilteredStreamFrameBody.builder()
                .suggestedQuery(queries)
                .build();
        return FilteredStreamFrame.metadata(body);
    }

}
