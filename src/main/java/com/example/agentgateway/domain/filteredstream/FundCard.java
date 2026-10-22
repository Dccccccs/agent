package com.example.agentgateway.domain.filteredstream;

import lombok.Builder;
import lombok.Getter;

/** 卡片 API 为一组基金生成的单张卡片。 */
@Getter
@Builder
public class FundCard {
    private final String cardId;
    private final String htmlContent;
}
