package com.example.agentgateway.infrastructure.filteredstream.client;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 临时卡片 API 响应：一组基金只生成一张卡片。 */
@Getter
@Setter
@NoArgsConstructor
public class FundCardApiResponse {
    private String cardId;
    private String htmlContent;
}
