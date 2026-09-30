package com.example.agentgateway.application.assembler.response;

import com.example.agentgateway.application.response.AgentResponse;
import com.example.agentgateway.application.response.FallbackAgentResponse;
import com.example.agentgateway.domain.agent.result.AgentResult;
import com.example.agentgateway.domain.agent.result.FallbackAgentResult;
import com.example.agentgateway.domain.exception.GatewayErrorCode;
import com.example.agentgateway.domain.exception.GatewayException;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FallbackAgentResponseAssembler extends AgentResponseAssembler {
    default String agentId() {
        return "fallback-agent";
    }

    FallbackAgentResponse assembleResponse(FallbackAgentResult result);

    default AgentResponse assemble(AgentResult result) {
        if (!(result instanceof FallbackAgentResult)) {
            throw new GatewayException(GatewayErrorCode.INVALID_AGENT_RESULT,
                    "兜底Agent响应转换类型错误");
        }
        return assembleResponse((FallbackAgentResult) result);
    }
}
