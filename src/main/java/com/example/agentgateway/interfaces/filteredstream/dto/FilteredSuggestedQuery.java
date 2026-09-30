package com.example.agentgateway.interfaces.filteredstream.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FilteredSuggestedQuery {
    private final String query;
    private final String intent;
}
