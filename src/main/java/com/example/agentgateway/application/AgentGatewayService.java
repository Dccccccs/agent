package com.example.agentgateway.application;

import com.example.agentgateway.application.agent.AgentRegistry;
import com.example.agentgateway.application.assembler.response.AgentResponseAssemblerRegistry;
import com.example.agentgateway.application.assembler.stream.AgentStreamChunkAssembler;
import com.example.agentgateway.application.pipeline.GatewayHandler;
import com.example.agentgateway.application.response.AgentResponse;
import com.example.agentgateway.application.response.stream.AgentStreamResponse;
import com.example.agentgateway.application.response.stream.DefaultAgentStreamResponse;
import com.example.agentgateway.domain.agent.StreamingAgentExecutor;
import com.example.agentgateway.domain.agent.stream.AgentStream;
import com.example.agentgateway.domain.exception.GatewayErrorCode;
import com.example.agentgateway.domain.exception.GatewayException;
import com.example.agentgateway.domain.model.AgentGatewayRequest;
import com.example.agentgateway.domain.model.AgentInfo;
import com.example.agentgateway.domain.model.GatewayContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AgentGatewayService {
    private final List<GatewayHandler> handlers;
    private final AgentInvocationMonitoringPublisher monitoringPublisher;
    private final AgentResponseAssemblerRegistry responseAssemblerRegistry;
    private final AgentRegistry agentRegistry;
    private final AgentStreamChunkAssembler streamChunkAssembler;

    public AgentGatewayService(List<GatewayHandler> handlers,
                               AgentInvocationMonitoringPublisher monitoringPublisher,
                               AgentResponseAssemblerRegistry responseAssemblerRegistry,
                               AgentRegistry agentRegistry,
                               AgentStreamChunkAssembler streamChunkAssembler) {
        this.handlers = handlers;
        this.monitoringPublisher = monitoringPublisher;
        this.responseAssemblerRegistry = responseAssemblerRegistry;
        this.agentRegistry = agentRegistry;
        this.streamChunkAssembler = streamChunkAssembler;
    }

    public AgentResponse query(AgentGatewayRequest request) {
        final long startedAt = System.currentTimeMillis();
        final Instant requestTime = Instant.ofEpochMilli(startedAt);
        GatewayContext context = null;
        RuntimeException failure = null;
        boolean streaming = false;
        boolean monitoringDeferred = false;
        try {
            context = GatewayContext.builder().request(request).build();
            for (GatewayHandler handler : handlers) {
                if (handler.supports(context)) {
                    handler.handle(context);
                }
            }

            streaming = agentRegistry.isStreaming(context.getAgentId());
            if (streaming) {
                AgentStreamResponse response = createStreamResponse(
                        request, context, requestTime, startedAt);
                monitoringDeferred = true;
                return response;
            }
            return responseAssemblerRegistry.assemble(context.getAgentId(), context.getResult());
        } catch (RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            if (!monitoringDeferred) {
                long completedAt = System.currentTimeMillis();
                monitoringPublisher.publish(request, context, requestTime,
                        Instant.ofEpochMilli(completedAt),
                        Math.max(completedAt - startedAt, 0L),
                        failure == null, failure,
                        streaming, streaming ? "START_FAILED" : null,
                        null, null);
            }
        }
    }

    private AgentStreamResponse createStreamResponse(
            final AgentGatewayRequest request,
            final GatewayContext context,
            final Instant requestTime,
            final long startedAt) {
        final StreamingAgentExecutor executor =
                agentRegistry.getRequiredStreaming(context.getAgentId());
        final AgentInfo agentInfo = executor.agentInfo();
        final AgentStream stream;
        try {
            stream = executor.executeStream(context);
        } catch (GatewayException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new GatewayException(GatewayErrorCode.AGENT_EXECUTION_FAILED, ex);
        }

        return new DefaultAgentStreamResponse(
                stream,
                streamChunkAssembler,
                new AgentGatewayStreamTerminalHandler(
                        request, context, requestTime, startedAt, agentInfo,
                        responseAssemblerRegistry, monitoringPublisher),
                startedAt);
    }
}
