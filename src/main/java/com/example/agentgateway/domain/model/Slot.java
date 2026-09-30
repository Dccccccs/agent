package com.example.agentgateway.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

@Getter
@Builder
public class Slot {
    @Singular(value = "mutualFundItem", ignoreNullCollections = true)
    private final List<MutualFundItem> mutualFund;
}
