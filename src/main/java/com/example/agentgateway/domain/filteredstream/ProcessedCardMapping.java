package com.example.agentgateway.domain.filteredstream;

import lombok.Builder;
import lombok.Getter;

/** 已完成校验和替换的卡片附加数据。 */
@Getter
@Builder
public class ProcessedCardMapping {
    private final String cardId;
    private final String htmlContent;
}
