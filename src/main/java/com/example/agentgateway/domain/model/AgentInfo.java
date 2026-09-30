package com.example.agentgateway.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.Map;

@Getter
@Builder
public class AgentInfo {
    private final String agentCode;
    private final String agentName;
    private final String version;
    private final Integer useOffline;
    @Singular(value = "attribute", ignoreNullCollections = true)
    private final Map<String, Object> attributes;

    public boolean usesOfflineResult() {
        return Integer.valueOf(1).equals(useOffline);
    }
}
