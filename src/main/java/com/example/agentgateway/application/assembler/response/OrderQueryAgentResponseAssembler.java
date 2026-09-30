package com.example.agentgateway.application.assembler.response;

import com.example.agentgateway.application.response.AgentResponse;
import com.example.agentgateway.application.response.OrderQueryAgentResponse;
import com.example.agentgateway.domain.agent.result.AgentResult;
import com.example.agentgateway.domain.agent.result.OrderQueryAgentResult;
import com.example.agentgateway.domain.exception.GatewayErrorCode;
import com.example.agentgateway.domain.exception.GatewayException;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrderQueryAgentResponseAssembler extends AgentResponseAssembler {
    default String agentId() {
        return "order-agent";
    }

    OrderQueryAgentResponse assembleResponse(OrderQueryAgentResult result);

    default AgentResponse assemble(AgentResult result) {
        if (!(result instanceof OrderQueryAgentResult)) {
            throw new GatewayException(GatewayErrorCode.INVALID_AGENT_RESULT,
                    "订单Agent响应转换类型错误");
        }
        return assembleResponse((OrderQueryAgentResult) result);
    }
}
