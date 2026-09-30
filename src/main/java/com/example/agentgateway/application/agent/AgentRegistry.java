package com.example.agentgateway.application.agent;

import com.example.agentgateway.domain.agent.AgentExecutor;
import com.example.agentgateway.domain.agent.StreamingAgentExecutor;
import com.example.agentgateway.domain.exception.GatewayErrorCode;
import com.example.agentgateway.domain.exception.GatewayException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AgentRegistry {
    private final Map<String, AgentExecutor> agents = new HashMap<String, AgentExecutor>();

    public AgentRegistry(List<AgentExecutor> agentList) {
        for (AgentExecutor agent : agentList) {
            AgentExecutor old = agents.put(agent.agentId(), agent);
            if (old != null) {
                throw new IllegalStateException("重复的Agent配置: " + agent.agentId());
            }
        }
    }

    public AgentExecutor getRequired(String agentId) {
        AgentExecutor agent = agents.get(agentId);
        if (agent == null) {
            throw new GatewayException(GatewayErrorCode.AGENT_NOT_FOUND, "未找到Agent: " + agentId);
        }
        return agent;
    }

    public boolean isStreaming(String agentId) {
        return getRequired(agentId) instanceof StreamingAgentExecutor;
    }

    public StreamingAgentExecutor getRequiredStreaming(String agentId) {
        AgentExecutor agent = getRequired(agentId);
        if (!(agent instanceof StreamingAgentExecutor)) {
            throw new GatewayException(GatewayErrorCode.INVALID_AGENT_RESULT,
                    "当前Agent不支持流式输出: " + agentId);
        }
        return (StreamingAgentExecutor) agent;
    }
}
