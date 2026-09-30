package com.example.agentgateway.interfaces.filteredstream.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FilteredStreamFrameBody {
    private final List<FilteredStreamAnswerBlock> answer;
    private final String operate;
    private final Object sections;
    private final String prompt;
    private final String riskTip;
    private final List<Object> agentAnswerSlots;
    private final List<FilteredSuggestedQuery> suggestedQuery;
    private final String recordId;
}
