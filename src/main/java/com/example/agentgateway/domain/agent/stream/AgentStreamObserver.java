package com.example.agentgateway.domain.agent.stream;

import com.example.agentgateway.domain.agent.result.AgentResult;

public interface AgentStreamObserver {
    void onNext(AgentStreamChunk chunk);
    void onComplete(AgentResult finalResult);
    void onError(Throwable throwable);
}
