package com.example.agentgateway.application.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderQueryAgentResponse implements AgentResponse {
    private final String recordId;
    private final String workflow;
    private final String answer;
    private final String uid;
}
