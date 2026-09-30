package com.example.agentgateway.application.response.stream;

import com.example.agentgateway.application.response.AgentResponse;

public interface AgentStreamResponseObserver {
    void onNext(AgentStreamChunkResponse chunk);
    void onComplete(AgentResponse finalResponse);
    void onError(Throwable throwable);
}
