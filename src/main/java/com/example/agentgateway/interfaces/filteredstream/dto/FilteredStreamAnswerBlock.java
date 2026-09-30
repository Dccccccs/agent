package com.example.agentgateway.interfaces.filteredstream.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class FilteredStreamAnswerBlock {
    private final String type;
    private final String content;
    private final List<FilteredLinkMapping> linkMapping;
    private final List<FilteredCardMapping> cardMapping;
}
