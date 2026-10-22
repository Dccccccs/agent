package com.example.agentgateway.infrastructure.filteredstream.client;

import lombok.Builder;
import lombok.Getter;

/** 临时卡片 API 请求：一次提交 m-card 容器匹配到的基金和副标题。 */
@Getter
@Builder
public class FundCardApiRequest {
    private final FundCardApiFundList fundList;
}
