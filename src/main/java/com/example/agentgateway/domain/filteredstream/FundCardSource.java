package com.example.agentgateway.domain.filteredstream;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Collections;

/** 一个已闭合 m-card 容器匹配出的基金，以及首帧提供的卡片副标题。 */
@Getter
@Builder
public class FundCardSource {
    private final String subtitle;
    private final List<FundDefinition> list;

    public static FundCardSource empty() {
        return FundCardSource.builder()
                .subtitle("")
                .list(Collections.emptyList())
                .build();
    }
}
