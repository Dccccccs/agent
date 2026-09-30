package com.example.agentgateway.domain.agent.result;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WeatherAgentResult implements OnlineAgentResult {
    private final String workflowId;
    private final String answer;
    private final String question;
}
