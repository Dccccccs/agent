package com.example.agentgateway.domain.filteredstream;

/** 外部模型首帧 content.status 支持的状态。 */
public enum MetadataStatus {
    /** 正常输出首帧并继续消费正文流。 */
    NORMAL,
    /** 输出首帧后终止上游，并追加兜底话术帧。 */
    FALLBACK
}
