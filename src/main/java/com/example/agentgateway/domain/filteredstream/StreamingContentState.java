package com.example.agentgateway.domain.filteredstream;

/** 跨帧内容检测状态。 */
public enum StreamingContentState {
    /** 普通文本，同时执行敏感词前缀检测。 */
    TEXT,
    /** 已读到 '<'，正在判断它是否是受控结构标签。 */
    TAG_CANDIDATE,
    /** 已确认 '<m-card'，等待完整的 '/>'。 */
    CARD,
    /** 已确认 '<url'，等待 '/>'。 */
    URL,
    /** 已确认 '<li'，等待完整的基金引用标签。 */
    FUND_ITEM,
    /** 已完成至少一个 li，暂存标签之间或标签后的空白。 */
    FUND_GROUP_GAP,
    /** 连续 li 组后再次读到 '<'，判断下一项是否仍为 li。 */
    FUND_GROUP_CANDIDATE
}
