package com.example.agentgateway.domain.agent.raw;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderQueryAgentRawResult {
    private final String recordId;
    private final String workflow;
    private final String answer;
    private final String uid;
}
