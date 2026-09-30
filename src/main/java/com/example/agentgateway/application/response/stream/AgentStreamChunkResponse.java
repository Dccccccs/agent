package com.example.agentgateway.application.response.stream;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AgentStreamChunkResponse {
    private final long sequence;
    private final String content;
}
