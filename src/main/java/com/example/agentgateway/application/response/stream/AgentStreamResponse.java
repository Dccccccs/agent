package com.example.agentgateway.application.response.stream;

import com.example.agentgateway.application.response.AgentResponse;

public interface AgentStreamResponse extends AgentResponse {
    void subscribe(AgentStreamResponseObserver observer);
    void cancel();
}
