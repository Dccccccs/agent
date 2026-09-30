package com.example.agentgateway.domain.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MatchedInfo {
    private final String name;
    private final String originName;
    private final String code;
    private final String shortName;
    private final String nameAbr;
    private final String standName;
    private final String fundType;
    private final String productType;
}
