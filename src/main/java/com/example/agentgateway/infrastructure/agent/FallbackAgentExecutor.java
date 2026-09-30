package com.example.agentgateway.infrastructure.agent;

import com.example.agentgateway.domain.agent.AgentExecutor;
import com.example.agentgateway.domain.agent.raw.FallbackAgentRawResult;
import com.example.agentgateway.domain.model.AgentInfo;
import com.example.agentgateway.domain.model.GatewayContext;
import org.springframework.stereotype.Component;

@Component
public class FallbackAgentExecutor implements AgentExecutor {
    public String agentId() {
        return "fallback-agent";
    }

    public AgentInfo agentInfo() {
        return AgentInfo.builder()
                .agentCode(agentId())
                .agentName("兜底Agent")
                .version("1.0.0")
                .useOffline(1)
                .build();
    }

    public FallbackAgentRawResult execute(GatewayContext context) {
        return FallbackAgentRawResult.builder()
                .recordId("fallback-record-" + context.getRequest().getMsgId())
                .answer("暂时无法识别您的意图，请换一种方式描述")
                .build();
    }
}
