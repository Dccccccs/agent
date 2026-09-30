package com.example.agentgateway.domain.agent.stream;

public interface AgentStream {
    void subscribe(AgentStreamObserver observer);
    void cancel();
}
