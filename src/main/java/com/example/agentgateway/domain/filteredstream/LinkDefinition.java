package com.example.agentgateway.domain.filteredstream;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LinkDefinition {
    private final String linkId;
    private final String linkContent;
}
