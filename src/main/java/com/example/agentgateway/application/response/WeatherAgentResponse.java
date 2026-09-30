package com.example.agentgateway.application.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WeatherAgentResponse implements AgentResponse {
    private final String workflowId;
    private final String answer;
    private final String question;
}
