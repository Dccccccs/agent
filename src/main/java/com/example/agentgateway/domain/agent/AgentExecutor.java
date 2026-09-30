package com.example.agentgateway.domain.agent;

import com.example.agentgateway.domain.model.AgentInfo;
import com.example.agentgateway.domain.model.GatewayContext;

public interface AgentExecutor {
    String agentId();
    AgentInfo agentInfo();
    Object execute(GatewayContext context);
}