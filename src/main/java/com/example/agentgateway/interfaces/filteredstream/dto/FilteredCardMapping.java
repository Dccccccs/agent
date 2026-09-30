package com.example.agentgateway.interfaces.filteredstream.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FilteredCardMapping {
    private final String cardId;
    private final String htmlContent;
}
