package com.example.agentgateway.domain.agent.raw;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WeatherAgentRawResult {
    private final String workflowId;
    private final String answer;
    private final String question;
}
