package com.example.agentgateway.interfaces.filteredstream.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FilteredOverRiskInfo {
    private final FilteredUserRiskInfo userRiskInfo;
    private final List<FilteredProductRiskInfo> productRiskInfo;
}
