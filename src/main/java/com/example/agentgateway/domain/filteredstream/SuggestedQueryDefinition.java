package com.example.agentgateway.domain.filteredstream;

import lombok.Builder;
import lombok.Getter;

/** 首帧 Metadata 中由上游提供的推荐问定义。 */
@Getter
@Builder
public class SuggestedQueryDefinition {
    private final String query;
    private final String intent;
}
