package com.example.agentgateway.application.filteredstream.model;

import com.example.agentgateway.domain.filteredstream.FallbackMessage;
import com.example.agentgateway.domain.filteredstream.ProcessedContentBlock;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 应用层输出事件。接口层负责将它转换为约定的 JSON/SSE 帧。
 */
@Getter
public class FilteredStreamEvent {
    /**
     * 事件顺序由应用服务保证：METADATA -> CONTENT* -> RECOMMENDATION? -> DONE；
     * 任意阶段失败时也保证 METADATA -> FALLBACK -> DONE。兜底答案已经在
     * 首帧 fallbackAnswer 中下发，FALLBACK 事件本身只负责通知客户端切换。
     */
    private final FilteredStreamEventType type;
    /** 已规范化的首帧 JSON；接口层会将其解析为 JSON 对象后发送。 */
    private final String metadataJson;
    /** 首帧固定使用请求 msgId 覆盖上游 recordId。 */
    private final String recordId;
    private final ProcessedContentBlock contentBlock;
    private final List<RecommendationQuery> recommendationQueries;
    /** 首帧预下发的结构化兜底回答。 */
    private final List<FallbackMessage> fallbackMessages;
    /** 仅正常完成时携带的上游原始正文全文。 */
    private final String originContent;

    private FilteredStreamEvent(FilteredStreamEventType type,
                                String metadataJson,
                                String recordId,
                                ProcessedContentBlock contentBlock,
                                List<RecommendationQuery> recommendationQueries,
                                List<FallbackMessage> fallbackMessages,
                                String originContent) {
        this.type = type;
        this.metadataJson = metadataJson;
        this.recordId = recordId;
        this.contentBlock = contentBlock;
        this.recommendationQueries = recommendationQueries;
        this.fallbackMessages = immutableFallbackMessages(fallbackMessages);
        this.originContent = originContent;
    }

    public static FilteredStreamEvent metadata(String metadataJson,
                                               String recordId,
                                               List<FallbackMessage> fallbackMessages) {
        return new FilteredStreamEvent(FilteredStreamEventType.METADATA,
                metadataJson, recordId, null, null, fallbackMessages, null);
    }

    /**
     * 上游首帧尚未到达便失败时使用的基础 Metadata 事件。接口层负责
     * 补充 recordId 和 fallbackAnswer 后输出。
     */
    public static FilteredStreamEvent emptyMetadata(
            String recordId,
            List<FallbackMessage> fallbackMessages) {
        return metadata(null, recordId, fallbackMessages);
    }

    public static FilteredStreamEvent content(ProcessedContentBlock block) {
        return new FilteredStreamEvent(FilteredStreamEventType.CONTENT,
                null, null, block, null, null, null);
    }

    public static FilteredStreamEvent recommendations(
            List<RecommendationQuery> recommendationQueries) {
        return new FilteredStreamEvent(FilteredStreamEventType.RECOMMENDATION,
                null, null, null,
                Collections.unmodifiableList(
                        new ArrayList<RecommendationQuery>(
                                recommendationQueries)),
                null, null);
    }

    public static FilteredStreamEvent fallback() {
        return new FilteredStreamEvent(FilteredStreamEventType.FALLBACK,
                null, null, null, null, null, null);
    }

    public static FilteredStreamEvent done() {
        return new FilteredStreamEvent(FilteredStreamEventType.DONE,
                null, null, null, null, null, null);
    }

    public static FilteredStreamEvent done(String originContent) {
        return new FilteredStreamEvent(FilteredStreamEventType.DONE,
                null, null, null, null, null, originContent);
    }

    private static List<FallbackMessage> immutableFallbackMessages(
            List<FallbackMessage> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(
                new ArrayList<FallbackMessage>(source));
    }
}
