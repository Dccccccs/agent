package com.example.agentgateway.infrastructure.converter;

import com.example.agentgateway.domain.agent.raw.FallbackAgentRawResult;
import com.example.agentgateway.domain.agent.result.AgentResultConverter;
import com.example.agentgateway.domain.agent.result.FallbackAgentResult;
import com.example.agentgateway.domain.exception.GatewayErrorCode;
import com.example.agentgateway.domain.exception.GatewayException;
import org.mapstruct.Mapper;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FallbackAgentResultConverter extends AgentResultConverter<FallbackAgentResult> {
    default String agentId() { return "fallback-agent"; }

    FallbackAgentResult toResult(FallbackAgentRawResult source);

    @Named("convertFallbackRootResult")
    default FallbackAgentResult convert(Object rawResult) {
        if (!(rawResult instanceof FallbackAgentRawResult)) {
            throw new GatewayException(GatewayErrorCode.INVALID_AGENT_RESULT, "兜底Agent返回结果类型错误");
        }
        return toResult((FallbackAgentRawResult) rawResult);
    }
}
