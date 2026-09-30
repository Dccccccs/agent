package com.example.agentgateway.domain.agent.raw;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FallbackAgentRawResult {
    private final String recordId;
    private final String answer;
}