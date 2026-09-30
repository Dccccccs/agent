package com.example.agentgateway.domain.agent.stream;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AgentStreamChunk {
    private final long sequence;
    private final String content;
}
