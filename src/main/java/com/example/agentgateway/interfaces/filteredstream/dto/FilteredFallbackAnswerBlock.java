package com.example.agentgateway.interfaces.filteredstream.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 首帧 fallbackAnswer 数组中的答案块。 */
@Getter
@Builder
public class FilteredFallbackAnswerBlock {
    private final String type;
    private final String content;
    private final List<FilteredLinkMapping> linkMapping;
    private final List<FilteredCardMapping> cardMapping;
}
