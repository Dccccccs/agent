package com.example.agentgateway.application.dto;

import lombok.Data;

import javax.validation.Valid;

@Data
public class MutualFundItem {
    private Integer start;
    private Integer end;
    private Integer realStart;
    private Integer realEnd;
    private String key;
    private String matched;

    @Valid
    private MatchedInfo matchedInfo;
}
