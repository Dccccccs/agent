package com.example.agentgateway.application.response.stream;

import com.example.agentgateway.application.assembler.stream.AgentStreamChunkAssembler;
import com.example.agentgateway.domain.agent.stream.AgentStream;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultAgentStreamResponse implements AgentStreamResponse {
    private final AgentStream stream;
    private final AgentStreamChunkAssembler chunkAssembler;
    private final AgentStreamTerminalHandler terminalHandler;
    private final long startedAt;
    private final AtomicBoolean subscribed = new AtomicBoolean(false);
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private final AtomicLong chunkCount = new AtomicLong(0L);
    private final AtomicLong firstChunkMillis = new AtomicLong(-1L);

    public DefaultAgentStreamResponse(AgentStream stream,
                                      AgentStreamChunkAssembler chunkAssembler,
                                      AgentStreamTerminalHandler terminalHandler,
                                      long startedAt) {
        this.stream = stream;
        this.chunkAssembler = chunkAssembler;
        this.terminalHandler = terminalHandler;
        this.startedAt = startedAt;
    }

    @Override
    public void subscribe(final AgentStreamResponseObserver observer) {
        if (!subscribed.compareAndSet(false, true)) {
            observer.onError(new IllegalStateException("流式响应只允许订阅一次"));
            return;
        }
        stream.subscribe(new DefaultAgentStreamObserver(
                observer, chunkAssembler, terminalHandler, terminated,
                chunkCount, firstChunkMillis, startedAt));
    }

    @Override
    public void cancel() {
        stream.cancel();
        if (terminated.compareAndSet(false, true)) {
            terminalHandler.onCancel(statistics());
        }
    }

    private StreamStatistics statistics() {
        long firstMillis = firstChunkMillis.get();
        return StreamStatistics.builder()
                .chunkCount(chunkCount.get())
                .firstChunkMillis(firstMillis < 0L ? null : firstMillis)
                .build();
    }
}
