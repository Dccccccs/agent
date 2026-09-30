package com.example.agentgateway.domain.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AgentGatewayRequest {
    private final String question;
    private final String msgId;
    private final String sessionId;
    private final String uid;
    private final String appId;
    private final String rewriteQuestion;
    private final Slot slot;
    private final Object extraParam;
}
