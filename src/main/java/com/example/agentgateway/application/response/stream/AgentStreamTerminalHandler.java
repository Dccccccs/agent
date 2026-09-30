package com.example.agentgateway.application.response.stream;

import com.example.agentgateway.application.response.AgentResponse;
import com.example.agentgateway.domain.agent.result.AgentResult;

public interface AgentStreamTerminalHandler {
    AgentResponse onComplete(AgentResult finalResult, StreamStatistics statistics);
    void onError(Throwable throwable, StreamStatistics statistics);
    void onCancel(StreamStatistics statistics);
}
