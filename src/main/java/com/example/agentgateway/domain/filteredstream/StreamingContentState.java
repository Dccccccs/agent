package com.example.agentgateway.domain.filteredstream;

/** 跨帧内容检测状态。 */
public enum StreamingContentState {
    /** 普通文本，同时执行敏感词前缀检测。 */
    TEXT,
    /** 已读到 '<'，正在判断它是否是 '<url' 或 '<m-card'。 */
    TAG_CANDIDATE,
    /** 已确认 '<m-card'，等待完整的 '/>'。 */
    CARD,
    /** 已确认 '<url'，等待 '/>'。 */
    URL
}
