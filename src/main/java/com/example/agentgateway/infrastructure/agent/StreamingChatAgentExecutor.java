package com.example.agentgateway.infrastructure.agent;

import com.example.agentgateway.domain.agent.StreamingAgentExecutor;
import com.example.agentgateway.domain.agent.stream.AgentStream;
import com.example.agentgateway.domain.model.AgentInfo;
import com.example.agentgateway.domain.model.GatewayContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

@Component
public class StreamingChatAgentExecutor implements StreamingAgentExecutor {
    private final Executor streamingExecutor;

    public StreamingChatAgentExecutor(
            @Qualifier("streamingExecutor") Executor streamingExecutor) {
        this.streamingExecutor = streamingExecutor;
    }

    @Override
    public String agentId() {
        return "stream-chat-agent";
    }

    @Override
    public AgentInfo agentInfo() {
        return AgentInfo.builder()
                .agentCode(agentId())
                .agentName("流式对话Agent")
                .version("1.0.0")
                .useOffline(0)
                .build();
    }

    @Override
    public AgentStream executeStream(GatewayContext context) {
        return new DemoAgentStream(
                context.getRequest().getMsgId(),
                context.getRequest().getQuestion(),
                streamingExecutor);
    }
}
