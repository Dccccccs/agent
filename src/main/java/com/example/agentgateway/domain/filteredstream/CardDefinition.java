package com.example.agentgateway.domain.filteredstream;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CardDefinition {
    /** 模型卡片标签 id 的值，例如 card_xxx。 */
    private final String cardPlaceHolder;
    private final String cardId;
    private final String cardName;
    private final String cardDescription;
    private final String htmlContent;
}
