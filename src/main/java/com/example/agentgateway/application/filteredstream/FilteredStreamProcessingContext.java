package com.example.agentgateway.application.filteredstream;

import com.example.agentgateway.domain.filteredstream.StreamMetadata;
import com.example.agentgateway.domain.filteredstream.StreamingContentProcessor;

/**
 * 一次上游流订阅独占的可变上下文。实例只存在于 Reactor 订阅范围内，
 * 不会保存到 Spring 单例字段中。
 */
final class FilteredStreamProcessingContext {
    private final StreamingContentProcessor processor;
    private final String recordId;
    /** 上游正文帧的原始 content，按接收顺序拼接，未经内容规则替换。 */
    private final StringBuilder originContent = new StringBuilder();
    private int originFrameCount;
    private StreamMetadata metadata = StreamMetadata.empty();
    private boolean metadataForwarded;
    private boolean contentStarted;
    private boolean upstreamFrameReceived;
    private boolean controlTerminated;
    private boolean contentProduced;

    FilteredStreamProcessingContext(StreamingContentProcessor processor,
                                    String recordId) {
        this.processor = processor;
        this.recordId = recordId;
    }

    StreamingContentProcessor getProcessor() {
        return processor;
    }

    String getRecordId() {
        return recordId;
    }

    int appendOriginContent(String content) {
        originContent.append(content);
        return ++originFrameCount;
    }

    String getOriginContent() {
        return originContent.toString();
    }

    /** 尚未收到上游正文帧时不在 DONE 中输出 originContent。 */
    String getOriginContentOrNull() {
        return originFrameCount == 0 ? null : originContent.toString();
    }

    StreamMetadata getMetadata() {
        return metadata;
    }

    void registerMetadata(StreamMetadata metadata) {
        this.metadata = metadata;
    }

    /** 只有 METADATA 事件真正进入下游事件流后，才能标记为已经输出。 */
    void markMetadataForwarded() {
        this.metadataForwarded = true;
    }

    boolean isMetadataForwarded() {
        return metadataForwarded;
    }

    void markContentStarted() {
        contentStarted = true;
    }

    boolean isContentStarted() {
        return contentStarted;
    }

    void markUpstreamFrameReceived() {
        upstreamFrameReceived = true;
    }

    boolean hasUpstreamFrameReceived() {
        return upstreamFrameReceived;
    }

    void markControlTerminated() {
        controlTerminated = true;
    }

    boolean isControlTerminated() {
        return controlTerminated;
    }

    void markContentProduced() {
        contentProduced = true;
    }

    boolean hasContentProduced() {
        return contentProduced;
    }
}
