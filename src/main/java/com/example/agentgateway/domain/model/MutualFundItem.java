package com.example.agentgateway.domain.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MutualFundItem {
    private final Integer start;
    private final Integer end;
    private final Integer realStart;
    private final Integer realEnd;
    private final String key;
    private final String matched;
    private final MatchedInfo matchedInfo;
}
