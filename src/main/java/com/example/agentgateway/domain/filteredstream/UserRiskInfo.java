package com.example.agentgateway.domain.filteredstream;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserRiskInfo {
    private final String userIsMinor;
    private final String userRiskLevel;
    private final String userRiskStatus;
}
