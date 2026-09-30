package com.example.agentgateway.domain.port;

public interface IntentAgentMappingPort {
    String findAgentIdByIntentId(String intentId);
}