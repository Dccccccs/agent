package com.example.agentgateway.interfaces.filteredstream.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FilteredUserRiskInfo {
    private final String userIsMinor;
    private final String userRiskLevel;
    private final String userRiskStatus;
}
