package com.example.agentgateway.interfaces.filteredstream.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FilteredLinkMapping {
    private final String linkId;
    private final String linkContent;
}
