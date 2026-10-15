package com.example.agentgateway.domain.filteredstream;

/** 跨帧内容检测状态。 */
public enum StreamingContentState {
    /** 普通文本，同时执行敏感词前缀检测。 */
    TEXT,
    /** 已读到 '<'，正在判断它是否是受控结构标签。 */
    TAG_CANDIDATE,
    /** 已确认 '<m-card'，判断是已有自闭合卡片还是基金容器开始标签。 */
    CARD,
    /** 已确认 '<url'，等待 '/>'。 */
    URL,
    /** 基金容器内已确认 '<li'，等待完整的基金引用标签。 */
    FUND_ITEM,
    /** 已进入基金容器，允许 li 标签及标签之间的空白。 */
    FUND_CARD_BODY,
    /** 基金容器内读到 '<'，判断是 li、结束标签或兜底标记。 */
    FUND_CARD_TAG_CANDIDATE,
    /** 已确认 '</m-card'，等待完整的结束标签。 */
    FUND_CARD_END
}
