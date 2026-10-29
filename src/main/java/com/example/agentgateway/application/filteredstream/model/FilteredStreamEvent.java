package com.example.agentgateway.application.filteredstream.model;

import com.example.agentgateway.domain.filteredstream.FallbackMessage;
import com.example.agentgateway.domain.filteredstream.OverRiskInfo;
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
    /** 已规范化的上游首帧 JSON，仅供首帧输出。 */
    private final String metadataJson;
    /** 首帧固定使用请求 msgId 覆盖上游 recordId。 */
    private final String recordId;
    private final ProcessedContentBlock contentBlock;
    private final List<RecommendationQuery> recommendationQueries;
    /** 正常结束时已按正文基金筛选的风险信息。 */
    private final OverRiskInfo overRiskInfo;
    /** 首帧预下发的结构化兜底回答。 */
    private final List<FallbackMessage> fallbackMessages;
    /** DONE 帧携带已收到的上游原始正文，正常结束或中途兜底均可使用。 */
    private final String originContent;
    /** FALLBACK 帧向下游说明本次兜底的原因。 */
    private final String fallbackReason;

    private FilteredStreamEvent(FilteredStreamEventType type,
                                String metadataJson,
                                String recordId,
                                ProcessedContentBlock contentBlock,
                                List<RecommendationQuery> recommendationQueries,
                                OverRiskInfo overRiskInfo,
                                List<FallbackMessage> fallbackMessages,
                                String originContent,
                                String fallbackReason) {
        this.type = type;
        this.metadataJson = metadataJson;
        this.recordId = recordId;
        this.contentBlock = contentBlock;
        this.recommendationQueries = recommendationQueries;
        this.overRiskInfo = overRiskInfo;
        this.fallbackMessages = immutableFallbackMessages(fallbackMessages);
        this.originContent = originContent;
        this.fallbackReason = fallbackReason;
    }

    public static FilteredStreamEvent metadata(String metadataJson,
                                               String recordId,
                                               List<FallbackMessage> fallbackMessages) {
        return new FilteredStreamEvent(FilteredStreamEventType.METADATA,
                metadataJson, recordId, null, null, null,
                fallbackMessages, null, null);
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
                null, null, block, null, null, null, null, null);
    }

    public static FilteredStreamEvent recommendations(
            List<RecommendationQuery> recommendationQueries) {
        return recommendations(recommendationQueries, null);
    }

    public static FilteredStreamEvent recommendations(
            List<RecommendationQuery> recommendationQueries,
            OverRiskInfo overRiskInfo) {
        return new FilteredStreamEvent(FilteredStreamEventType.RECOMMENDATION,
                null, null, null,
                Collections.unmodifiableList(
                        new ArrayList<RecommendationQuery>(
                                recommendationQueries)),
                overRiskInfo, null, null, null);
    }

    public static FilteredStreamEvent fallback(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("兜底原因不能为空");
        }
        return new FilteredStreamEvent(FilteredStreamEventType.FALLBACK,
                null, null, null, null, null, null, null, reason);
    }

    public static FilteredStreamEvent done() {
        return new FilteredStreamEvent(FilteredStreamEventType.DONE,
                null, null, null, null, null, null, null, null);
    }

    public static FilteredStreamEvent done(String originContent) {
        return new FilteredStreamEvent(FilteredStreamEventType.DONE,
                null, null, null, null, null, null, originContent, null);
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
