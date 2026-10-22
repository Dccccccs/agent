package com.example.agentgateway.domain.filteredstream;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductRiskInfo {
    private final String productName;
    private final String productCode;
    private final String bscRskLvl;
}
