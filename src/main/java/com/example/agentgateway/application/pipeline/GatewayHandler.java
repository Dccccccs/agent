package com.example.agentgateway.application.pipeline;

import com.example.agentgateway.domain.model.GatewayContext;

public interface GatewayHandler {
    default boolean supports(GatewayContext context) {
        return true;
    }

    void handle(GatewayContext context);
}
