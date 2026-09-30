package com.example.agentgateway.infrastructure.monitoring;

import com.example.agentgateway.application.monitoring.InvocationMonitoringEvent;
import com.example.agentgateway.application.port.InvocationMonitoringSink;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j(topic = "AGENT_MONITOR")
public class LoggingInvocationMonitoringSink implements InvocationMonitoringSink {
    private final ObjectMapper objectMapper;

    public LoggingInvocationMonitoringSink(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(InvocationMonitoringEvent event) {
        try {
            log.info(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("监控事件序列化失败", ex);
        }
    }
}
