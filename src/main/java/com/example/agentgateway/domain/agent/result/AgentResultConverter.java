package com.example.agentgateway.domain.agent.result;

public interface AgentResultConverter<T extends AgentResult> {
    String agentId();
    T convert(Object rawResult);
}