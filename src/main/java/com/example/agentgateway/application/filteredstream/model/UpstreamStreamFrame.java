package com.example.agentgateway.application.filteredstream.model;

import com.example.agentgateway.domain.filteredstream.StreamMetadata;
import lombok.Getter;

/**
 * 应用层可理解的统一上游帧，不暴露 WebClient 或上游厂商协议。
 *
 * <p>外部的 status/returnType/choices 等不同结构经过解码器后，只剩三类信息：</p>
 * <ul>
 *     <li>metadata=true：元数据首帧，同时携带规范化 metadataJson 和强类型 metadataContent；</li>
 *     <li>content 非空：需要进入逐帧规则处理的一段增量正文；</li>
 *     <li>done=true：上游自然结束标记，不直接作为正文发送。</li>
 * </ul>
 */
@Getter
public class UpstreamStreamFrame {
    /** 当前帧的正文增量；Metadata 和纯 DONE 时为空。 */
    private final String content;
    /** true 表示消费完本帧后应结束上游；END 帧可以同时包含 content。 */
    private final Boolean done;
    /** 是否为流的静态元数据首帧。 */
    private final Boolean metadata;
    /**
     * 已转换为目标协议的 Metadata JSON：
     * {"body":{...},"returnType":"METADATA"}。
     */
    private final String metadataJson;
    /** 从首帧 body 解析出的强类型 Metadata 对象。 */
    private final StreamMetadata metadataContent;

    private UpstreamStreamFrame(String content,
                                Boolean done,
                                Boolean metadata,
                                String metadataJson,
                                StreamMetadata metadataContent) {
        this.content = content;
        // 包装类型满足传输模型规范，同时在入口归一化，避免后续拆箱 NPE。
        this.done = Boolean.TRUE.equals(done);
        this.metadata = Boolean.TRUE.equals(metadata);
        this.metadataJson = metadataJson;
        this.metadataContent = metadataContent == null
                ? StreamMetadata.empty() : metadataContent;
    }

    public static UpstreamStreamFrame content(String content) {
        return new UpstreamStreamFrame(content, false, false, null,
                StreamMetadata.empty());
    }

    public static UpstreamStreamFrame content(String content, Boolean done) {
        return new UpstreamStreamFrame(content, done, false, null,
                StreamMetadata.empty());
    }

    public static UpstreamStreamFrame metadata(String metadataJson) {
        return metadata(metadataJson, StreamMetadata.empty());
    }

    public static UpstreamStreamFrame metadata(
            String metadataJson,
            StreamMetadata metadataContent) {
        return new UpstreamStreamFrame(null, false, true, metadataJson,
                metadataContent);
    }

    public static UpstreamStreamFrame done() {
        return new UpstreamStreamFrame(null, true, false, null,
                StreamMetadata.empty());
    }

    public static UpstreamStreamFrame skip() {
        return new UpstreamStreamFrame(null, false, false, null,
                StreamMetadata.empty());
    }

    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }

    public boolean isDeliverable() {
        return Boolean.TRUE.equals(metadata) || hasContent();
    }
}
