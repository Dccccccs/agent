package com.example.agentgateway.infrastructure.agent;

import com.example.agentgateway.domain.agent.AgentExecutor;
import com.example.agentgateway.domain.agent.raw.WeatherAgentRawResult;
import com.example.agentgateway.domain.model.AgentInfo;
import com.example.agentgateway.domain.model.GatewayContext;
import org.springframework.stereotype.Component;

@Component
public class WeatherAgentExecutor implements AgentExecutor {
    public String agentId() {
        return "weather-agent";
    }

    public AgentInfo agentInfo() {
        return AgentInfo.builder()
                .agentCode(agentId())
                .agentName("天气Agent")
                .version("1.0.0")
                .useOffline(0)
                .build();
    }

    public WeatherAgentRawResult execute(GatewayContext context) {
        return WeatherAgentRawResult.builder()
                .workflowId("weather-workflow-" + context.getRequest().getMsgId())
                .answer("天气Agent已执行，实际项目中此处调用在线天气工作流")
                .question(context.getRequest().getQuestion())
                .build();
    }
}
