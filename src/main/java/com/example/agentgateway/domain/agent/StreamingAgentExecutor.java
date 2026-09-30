package com.example.agentgateway.domain.agent;

import com.example.agentgateway.domain.agent.stream.AgentStream;
import com.example.agentgateway.domain.model.GatewayContext;

public interface StreamingAgentExecutor extends AgentExecutor {
    AgentStream executeStream(GatewayContext context);

    @Override
    default Object execute(GatewayContext context) {
        throw new UnsupportedOperationException("流式Agent不支持同步execute");
    }
}
