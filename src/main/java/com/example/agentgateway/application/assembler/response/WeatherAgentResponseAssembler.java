package com.example.agentgateway.application.assembler.response;

import com.example.agentgateway.application.response.AgentResponse;
import com.example.agentgateway.application.response.WeatherAgentResponse;
import com.example.agentgateway.domain.agent.result.AgentResult;
import com.example.agentgateway.domain.agent.result.WeatherAgentResult;
import com.example.agentgateway.domain.exception.GatewayErrorCode;
import com.example.agentgateway.domain.exception.GatewayException;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface WeatherAgentResponseAssembler extends AgentResponseAssembler {
    default String agentId() {
        return "weather-agent";
    }

    WeatherAgentResponse assembleResponse(WeatherAgentResult result);

    default AgentResponse assemble(AgentResult result) {
        if (!(result instanceof WeatherAgentResult)) {
            throw new GatewayException(GatewayErrorCode.INVALID_AGENT_RESULT,
                    "天气Agent响应转换类型错误");
        }
        return assembleResponse((WeatherAgentResult) result);
    }
}
