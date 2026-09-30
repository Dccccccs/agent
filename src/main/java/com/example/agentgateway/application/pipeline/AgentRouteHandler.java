package com.example.agentgateway.application.pipeline;

import com.example.agentgateway.domain.exception.GatewayErrorCode;
import com.example.agentgateway.domain.exception.GatewayException;
import com.example.agentgateway.domain.model.GatewayContext;
import com.example.agentgateway.domain.port.IntentAgentMappingPort;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class AgentRouteHandler implements GatewayHandler {
    private final IntentAgentMappingPort mappingPort;

    public AgentRouteHandler(IntentAgentMappingPort mappingPort) {
        this.mappingPort = mappingPort;
    }

    public void handle(GatewayContext context) {
        final String agentId;
        try {
            agentId = mappingPort.findAgentIdByIntentId(context.getIntentId());
        } catch (GatewayException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new GatewayException(GatewayErrorCode.AGENT_ROUTE_FAILED, ex);
        }
        if (agentId == null || agentId.trim().isEmpty()) {
            throw new GatewayException(GatewayErrorCode.AGENT_MAPPING_NOT_FOUND,
                    "意图未配置Agent映射: " + context.getIntentId());
        }
        context.routeToAgent(agentId);
    }
}