package com.example.agentgateway.infrastructure.client;

import com.example.agentgateway.domain.port.IntentAgentMappingPort;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class InMemoryIntentAgentMappingAdapter implements IntentAgentMappingPort {
    private final Map<String, String> mappings;

    public InMemoryIntentAgentMappingAdapter() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("WEATHER_INTENT", "weather-agent");
        values.put("ORDER_QUERY_INTENT", "order-agent");
        values.put("STREAM_CHAT_INTENT", "stream-chat-agent");
        values.put("UNKNOWN_INTENT", "fallback-agent");
        this.mappings = Collections.unmodifiableMap(values);
    }

    public String findAgentIdByIntentId(String intentId) {
        return mappings.get(intentId);
    }
}
