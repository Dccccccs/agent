package com.example.agentgateway.infrastructure.agent;

import com.example.agentgateway.domain.agent.AgentExecutor;
import com.example.agentgateway.domain.agent.raw.OrderQueryAgentRawResult;
import com.example.agentgateway.domain.model.AgentInfo;
import com.example.agentgateway.domain.model.GatewayContext;
import org.springframework.stereotype.Component;

@Component
public class OrderQueryAgentExecutor implements AgentExecutor {
    public String agentId() {
        return "order-agent";
    }

    public AgentInfo agentInfo() {
        return AgentInfo.builder()
                .agentCode(agentId())
                .agentName("订单Agent")
                .version("1.0.0")
                .useOffline(1)
                .build();
    }

    public OrderQueryAgentRawResult execute(GatewayContext context) {
        return OrderQueryAgentRawResult.builder()
                .recordId("order-record-" + context.getRequest().getMsgId())
                .workflow("order-query-offline-task")
                .answer("订单Agent已执行，返回离线结果")
                .uid(context.getRequest().getUid())
                .build();
    }
}
