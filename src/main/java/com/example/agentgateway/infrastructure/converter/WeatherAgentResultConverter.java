package com.example.agentgateway.infrastructure.converter;

import com.example.agentgateway.domain.agent.raw.WeatherAgentRawResult;
import com.example.agentgateway.domain.agent.result.AgentResultConverter;
import com.example.agentgateway.domain.agent.result.WeatherAgentResult;
import com.example.agentgateway.domain.exception.GatewayErrorCode;
import com.example.agentgateway.domain.exception.GatewayException;
import org.mapstruct.Mapper;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface WeatherAgentResultConverter extends AgentResultConverter<WeatherAgentResult> {
    default String agentId() { return "weather-agent"; }

    WeatherAgentResult toResult(WeatherAgentRawResult source);

    @Named("convertWeatherRootResult")
    default WeatherAgentResult convert(Object rawResult) {
        if (!(rawResult instanceof WeatherAgentRawResult)) {
            throw new GatewayException(GatewayErrorCode.INVALID_AGENT_RESULT, "天气Agent返回结果类型错误");
        }
        return toResult((WeatherAgentRawResult) rawResult);
    }
}
