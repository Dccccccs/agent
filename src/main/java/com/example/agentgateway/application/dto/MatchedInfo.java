package com.example.agentgateway.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MatchedInfo {
    private String name;

    @JsonProperty("origin_name")
    private String originName;

    private String code;

    @JsonProperty("shortName")
    private String shortName;

    @JsonProperty("name_abr")
    private String nameAbr;

    @JsonProperty("stand_name")
    private String standName;

    @JsonProperty("fund_type")
    private String fundType;

    @JsonProperty("product_type")
    private String productType;
}
