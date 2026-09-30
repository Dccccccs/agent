package com.example.agentgateway.application.assembler;

import com.example.agentgateway.application.monitoring.InvocationMonitoringEvent;
import com.example.agentgateway.domain.agent.result.AgentResult;
import com.example.agentgateway.domain.agent.result.OfflineAgentResult;
import com.example.agentgateway.domain.agent.result.OnlineAgentResult;
import com.example.agentgateway.domain.model.AgentGatewayRequest;
import com.example.agentgateway.domain.model.GatewayContext;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface InvocationMonitoringEventAssembler {
    DateTimeFormatter MONITORING_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Mapping(target = "question", source = "request.question")
    @Mapping(target = "msgId", source = "request.msgId")
    @Mapping(target = "sessionId", source = "request.sessionId")
    @Mapping(target = "uid", source = "request.uid")
    @Mapping(target = "appId", source = "request.appId")
    @Mapping(target = "rewriteQuestion", source = "request.rewriteQuestion")
    @Mapping(target = "slot", source = "request.slot")
    @Mapping(target = "extraParam", source = "request.extraParam")
    @Mapping(target = "intentId", source = "context.intentId")
    @Mapping(target = "intentResult", source = "context.intentResult")
    @Mapping(target = "agentId", source = "context.agentId")
    @Mapping(target = "useOffline", source = "context.agentInfo.useOffline")
    @Mapping(target = "recordId", expression = "java(extractRecordId(context == null ? null : context.getResult()))")
    @Mapping(target = "workflowId", expression = "java(extractWorkflowId(context == null ? null : context.getResult()))")
    @Mapping(target = "agentResultId", source = "context.agentResultId")
    @Mapping(target = "requestTime", expression = "java(formatTime(requestTime))")
    @Mapping(target = "responseTime", expression = "java(formatTime(responseTime))")
    @Mapping(target = "intentRequestTime", expression = "java(formatTime(context == null ? null : context.getIntentRequestTime()))")
    @Mapping(target = "intentResponseTime", expression = "java(formatTime(context == null ? null : context.getIntentResponseTime()))")
    @Mapping(target = "intentRecognitionMillis", source = "context.intentRecognitionMillis")
    @Mapping(target = "totalMillis", source = "totalMillis")
    @Mapping(target = "streaming", source = "streaming")
    @Mapping(target = "streamEndReason", source = "streamEndReason")
    @Mapping(target = "chunkCount", source = "chunkCount")
    @Mapping(target = "firstChunkMillis", source = "firstChunkMillis")
    @Mapping(target = "agentInfo", source = "context.agentInfo")
    @Mapping(target = "answer", source = "context.result")
    @Mapping(target = "success", source = "success")
    @Mapping(target = "errorCode", source = "errorCode")
    @Mapping(target = "errorMessage", source = "errorMessage")
    @Mapping(target = "extensions", ignore = true)
    InvocationMonitoringEvent assemble(AgentGatewayRequest request, GatewayContext context,
                                      Instant requestTime, Instant responseTime,
                                      long totalMillis, boolean success,
                                      String errorCode, String errorMessage,
                                      Boolean streaming, String streamEndReason,
                                      Long chunkCount, Long firstChunkMillis);

    default String formatTime(Instant time) {
        return time == null ? null : MONITORING_TIME_FORMATTER.format(time);
    }

    default String extractRecordId(AgentResult result) {
        return result instanceof OfflineAgentResult
                ? ((OfflineAgentResult) result).getRecordId() : null;
    }

    default String extractWorkflowId(AgentResult result) {
        return result instanceof OnlineAgentResult
                ? ((OnlineAgentResult) result).getWorkflowId() : null;
    }
}
