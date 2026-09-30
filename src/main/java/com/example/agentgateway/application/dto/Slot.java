package com.example.agentgateway.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;

@Data
public class Slot {
    @Valid
    @JsonProperty("MutualFund")
    private List<MutualFundItem> mutualFund = new ArrayList<MutualFundItem>();
}
