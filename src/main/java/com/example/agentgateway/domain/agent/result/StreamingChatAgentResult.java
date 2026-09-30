package com.example.agentgateway.domain.agent.result;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StreamingChatAgentResult implements OnlineAgentResult {
    private final String workflowId;
    private final String answer;
}
