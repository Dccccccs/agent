package com.example.agentgateway.application.assembler.result;

import com.example.agentgateway.domain.agent.result.AgentResult;
import com.example.agentgateway.domain.agent.result.AgentResultConverter;
import com.example.agentgateway.domain.exception.GatewayErrorCode;
import com.example.agentgateway.domain.exception.GatewayException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AgentResultAssemblerRegistry {
    private final Map<String, AgentResultConverter<? extends AgentResult>> converters =
            new HashMap<String, AgentResultConverter<? extends AgentResult>>();

    public AgentResultAssemblerRegistry(List<AgentResultConverter<? extends AgentResult>> converterList) {
        for (AgentResultConverter<? extends AgentResult> converter : converterList) {
            AgentResultConverter<? extends AgentResult> old = converters.put(
                    converter.agentId(), converter);
            if (old != null) {
                throw new IllegalStateException("重复的Agent结果转换器: " + converter.agentId());
            }
        }
    }

    public AgentResult assemble(String agentId, Object rawResult) {
        AgentResultConverter<? extends AgentResult> converter = converters.get(agentId);
        if (converter == null) {
            throw new GatewayException(GatewayErrorCode.AGENT_RESULT_CONVERTER_NOT_FOUND,
                    "未配置Agent结果转换器: " + agentId);
        }
        return converter.convert(rawResult);
    }
}
