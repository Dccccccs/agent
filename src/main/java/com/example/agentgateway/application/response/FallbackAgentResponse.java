package com.example.agentgateway.application.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FallbackAgentResponse implements AgentResponse {
    private final String recordId;
    private final String answer;
}
