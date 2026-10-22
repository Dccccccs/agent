package com.example.agentgateway.infrastructure.filteredstream.client;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 卡片 API 入参 fundList 对象。 */
@Getter
@Builder
public class FundCardApiFundList {
    private final String subtitle;
    private final List<FundCardApiFund> list;
}
