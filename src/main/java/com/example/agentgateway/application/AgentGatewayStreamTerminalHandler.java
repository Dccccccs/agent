package com.example.agentgateway.application;

import com.example.agentgateway.application.assembler.response.AgentResponseAssemblerRegistry;
import com.example.agentgateway.application.response.AgentResponse;
import com.example.agentgateway.application.response.stream.AgentStreamTerminalHandler;
import com.example.agentgateway.application.response.stream.StreamStatistics;
import com.example.agentgateway.domain.agent.result.AgentResult;
import com.example.agentgateway.domain.exception.GatewayErrorCode;
import com.example.agentgateway.domain.exception.GatewayException;
import com.example.agentgateway.domain.model.AgentGatewayRequest;
import com.example.agentgateway.domain.model.AgentInfo;
import com.example.agentgateway.domain.model.GatewayContext;

import java.time.Instant;

/** 处理流式 Agent 完成、失败与取消三种终态。 */
final class AgentGatewayStreamTerminalHandler
        implements AgentStreamTerminalHandler {
    private final AgentGatewayRequest request;
    private final GatewayContext context;
    private final Instant requestTime;
    private final long startedAt;
    private final AgentInfo agentInfo;
    private final AgentResponseAssemblerRegistry responseAssemblerRegistry;
    private final AgentInvocationMonitoringPublisher monitoringPublisher;

    AgentGatewayStreamTerminalHandler(
            AgentGatewayRequest request,
            GatewayContext context,
            Instant requestTime,
            long startedAt,
            AgentInfo agentInfo,
            AgentResponseAssemblerRegistry responseAssemblerRegistry,
            AgentInvocationMonitoringPublisher monitoringPublisher) {
        this.request = request;
        this.context = context;
        this.requestTime = requestTime;
        this.startedAt = startedAt;
        this.agentInfo = agentInfo;
        this.responseAssemblerRegistry = responseAssemblerRegistry;
        this.monitoringPublisher = monitoringPublisher;
    }

    @Override
    public AgentResponse onComplete(AgentResult finalResult,
                                    StreamStatistics statistics) {
        try {
            context.completeAgentExecution(agentInfo, finalResult);
        } catch (IllegalArgumentException ex) {
            throw new GatewayException(
                    GatewayErrorCode.INVALID_AGENT_RESULT,
                    ex.getMessage(), ex);
        }
        AgentResponse response = responseAssemblerRegistry.assemble(
                context.getAgentId(), finalResult);
        monitoringPublisher.publishStream(request, context, requestTime,
                startedAt, true, null, "COMPLETED", statistics);
        return response;
    }

    @Override
    public void onError(Throwable throwable, StreamStatistics statistics) {
        monitoringPublisher.publishStream(request, context, requestTime,
                startedAt, false, asRuntimeException(throwable),
                "ERROR", statistics);
    }

    @Override
    public void onCancel(StreamStatistics statistics) {
        monitoringPublisher.publishStream(request, context, requestTime,
                startedAt, false,
                new GatewayException(GatewayErrorCode.AGENT_EXECUTION_FAILED,
                        "流式Agent调用已取消"),
                "CANCELLED", statistics);
    }

    private RuntimeException asRuntimeException(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            return (RuntimeException) throwable;
        }
        return new GatewayException(
                GatewayErrorCode.AGENT_EXECUTION_FAILED, throwable);
    }
}
