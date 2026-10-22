package com.example.agentgateway.domain.filteredstream;

import lombok.Builder;
import lombok.Getter;

/** 首帧 fundList 中用于生成组合基金卡片的可信基金数据。 */
@Getter
@Builder
public class FundDefinition {
    private final String fundId;
    private final String fundName;
    private final String fundDesc;
}
