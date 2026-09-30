package com.example.agentgateway.domain.agent.result;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderQueryAgentResult implements OfflineAgentResult {
    private final String recordId;
    private final String workflow;
    private final String answer;
    private final String uid;
}
