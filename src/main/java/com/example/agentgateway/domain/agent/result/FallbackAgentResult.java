package com.example.agentgateway.domain.agent.result;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FallbackAgentResult implements OfflineAgentResult {
    private final String recordId;
    private final String answer;
}
