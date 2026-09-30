package com.example.agentgateway.application.assembler.response;

import com.example.agentgateway.application.response.AgentResponse;
import com.example.agentgateway.domain.agent.result.AgentResult;

public interface AgentResponseAssembler {
    String agentId();
    AgentResponse assemble(AgentResult result);
}
