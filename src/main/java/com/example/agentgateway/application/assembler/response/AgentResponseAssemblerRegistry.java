package com.example.agentgateway.application.assembler.response;

import com.example.agentgateway.application.response.AgentResponse;
import com.example.agentgateway.domain.agent.result.AgentResult;
import com.example.agentgateway.domain.exception.GatewayErrorCode;
import com.example.agentgateway.domain.exception.GatewayException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AgentResponseAssemblerRegistry {
    private final Map<String, AgentResponseAssembler> assemblers =
            new HashMap<String, AgentResponseAssembler>();

    public AgentResponseAssemblerRegistry(List<AgentResponseAssembler> assemblerList) {
        for (AgentResponseAssembler assembler : assemblerList) {
            AgentResponseAssembler previous = assemblers.put(assembler.agentId(), assembler);
            if (previous != null) {
                throw new IllegalStateException(
                        "重复的Agent应用响应装配器: " + assembler.agentId());
            }
        }
    }

    public AgentResponse assemble(String agentId, AgentResult result) {
        AgentResponseAssembler assembler = assemblers.get(agentId);
        if (assembler == null) {
            throw new GatewayException(
                    GatewayErrorCode.AGENT_RESULT_CONVERTER_NOT_FOUND,
                    "未配置Agent应用响应装配器: " + agentId);
        }
        return assembler.assemble(result);
    }
}
