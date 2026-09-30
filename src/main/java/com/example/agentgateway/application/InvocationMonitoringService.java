package com.example.agentgateway.application;

import com.example.agentgateway.application.monitoring.InvocationMonitoringEvent;
import com.example.agentgateway.application.port.InvocationMonitoringSink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class InvocationMonitoringService {
    private final List<InvocationMonitoringSink> sinks;

    public InvocationMonitoringService(List<InvocationMonitoringSink> sinks) {
        this.sinks = sinks;
    }

    @Async("monitoringExecutor")
    public void publish(InvocationMonitoringEvent event) {
        for (InvocationMonitoringSink sink : sinks) {
            try {
                sink.save(event);
            } catch (Exception ex) {
                log.error("保存 Agent 监控事件失败, msgId={}, sink={}",
                        event.getMsgId(), sink.getClass().getSimpleName(), ex);
            }
        }
    }
}
