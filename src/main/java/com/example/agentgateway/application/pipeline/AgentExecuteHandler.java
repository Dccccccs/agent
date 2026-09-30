package com.example.agentgateway.application.pipeline;

import com.example.agentgateway.application.agent.AgentRegistry;
import com.example.agentgateway.application.assembler.result.AgentResultAssemblerRegistry;
import com.example.agentgateway.domain.agent.AgentExecutor;
import com.example.agentgateway.domain.agent.result.AgentResult;
import com.example.agentgateway.domain.exception.GatewayErrorCode;
import com.example.agentgateway.domain.exception.GatewayException;
import com.example.agentgateway.domain.model.GatewayContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
public class AgentExecuteHandler implements GatewayHandler {
    private final AgentRegistry agentRegistry;
    private final AgentResultAssemblerRegistry resultAssemblerRegistry;

    public AgentExecuteHandler(AgentRegistry agentRegistry,
                               AgentResultAssemblerRegistry resultAssemblerRegistry) {
        this.agentRegistry = agentRegistry;
        this.resultAssemblerRegistry = resultAssemblerRegistry;
    }

    @Override
    public boolean supports(GatewayContext context) {
        return !agentRegistry.isStreaming(context.getAgentId());
    }

    @Override
    public void handle(GatewayContext context) {
        AgentExecutor agent = agentRegistry.getRequired(context.getAgentId());
        Object rawResult;
        try {
            rawResult = agent.execute(context);
        } catch (GatewayException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new GatewayException(GatewayErrorCode.AGENT_EXECUTION_FAILED, ex);
        }

        final AgentResult result;
        try {
            result = resultAssemblerRegistry.assemble(context.getAgentId(), rawResult);
        } catch (GatewayException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new GatewayException(GatewayErrorCode.AGENT_RESULT_CONVERT_FAILED, ex);
        }
        try {
            context.completeAgentExecution(agent.agentInfo(), result);
        } catch (IllegalArgumentException ex) {
            throw new GatewayException(GatewayErrorCode.INVALID_AGENT_RESULT, ex.getMessage(), ex);
        }
    }
}
