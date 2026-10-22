package com.example.agentgateway.infrastructure.filteredstream.client;

import lombok.Builder;
import lombok.Getter;

/** 临时卡片 API 请求中的基金对象，后续可按真实契约替换。 */
@Getter
@Builder
public class FundCardApiFund {
    private final String fundId;
    private final String fundName;
    private final String fundDesc;
}
