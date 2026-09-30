package com.example.agentgateway.application;

import com.example.agentgateway.application.assembler.InvocationMonitoringEventAssembler;
import com.example.agentgateway.application.response.stream.StreamStatistics;
import com.example.agentgateway.domain.exception.GatewayErrorCode;
import com.example.agentgateway.domain.exception.GatewayException;
import com.example.agentgateway.domain.model.AgentGatewayRequest;
import com.example.agentgateway.domain.model.GatewayContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** 集中构建并安全发布 Agent 调用监控事件。 */
@Component
@Slf4j
public class AgentInvocationMonitoringPublisher {
    private final InvocationMonitoringService monitoringService;
    private final InvocationMonitoringEventAssembler monitoringEventAssembler;

    public AgentInvocationMonitoringPublisher(
            InvocationMonitoringService monitoringService,
            InvocationMonitoringEventAssembler monitoringEventAssembler) {
        this.monitoringService = monitoringService;
        this.monitoringEventAssembler = monitoringEventAssembler;
    }

    public void publish(AgentGatewayRequest request,
                        GatewayContext context,
                        Instant requestTime,
                        Instant responseTime,
                        long totalMillis,
                        boolean success,
                        RuntimeException failure,
                        boolean streaming,
                        String streamEndReason,
                        Long chunkCount,
                        Long firstChunkMillis) {
        try {
            monitoringService.publish(monitoringEventAssembler.assemble(
                    request, context, requestTime, responseTime, totalMillis,
                    success, errorCode(failure), errorMessage(failure),
                    streaming, streamEndReason, chunkCount, firstChunkMillis));
        } catch (RuntimeException monitoringException) {
            log.error("发布Agent调用监控失败, msgId={}",
                    request == null ? null : request.getMsgId(),
                    monitoringException);
        }
    }

    public void publishStream(AgentGatewayRequest request,
                              GatewayContext context,
                              Instant requestTime,
                              long startedAt,
                              boolean success,
                              RuntimeException failure,
                              String endReason,
                              StreamStatistics statistics) {
        long completedAt = System.currentTimeMillis();
        publish(request, context, requestTime,
                Instant.ofEpochMilli(completedAt),
                Math.max(completedAt - startedAt, 0L),
                success, failure, true, endReason,
                statistics.getChunkCount(), statistics.getFirstChunkMillis());
    }

    private String errorCode(RuntimeException failure) {
        if (failure == null) {
            return null;
        }
        return failure instanceof GatewayException
                ? ((GatewayException) failure).getCode()
                : GatewayErrorCode.SYSTEM_ERROR.getCode();
    }

    private String errorMessage(RuntimeException failure) {
        return failure == null ? null : failure.getMessage();
    }
}
