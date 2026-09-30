package com.example.agentgateway.application.response.stream;

import com.example.agentgateway.application.assembler.stream.AgentStreamChunkAssembler;
import com.example.agentgateway.application.response.AgentResponse;
import com.example.agentgateway.domain.agent.result.AgentResult;
import com.example.agentgateway.domain.agent.stream.AgentStreamChunk;
import com.example.agentgateway.domain.agent.stream.AgentStreamObserver;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** 将领域流事件适配为应用层流式响应事件。 */
final class DefaultAgentStreamObserver implements AgentStreamObserver {
    private final AgentStreamResponseObserver responseObserver;
    private final AgentStreamChunkAssembler chunkAssembler;
    private final AgentStreamTerminalHandler terminalHandler;
    private final AtomicBoolean terminated;
    private final AtomicLong chunkCount;
    private final AtomicLong firstChunkMillis;
    private final long startedAt;

    DefaultAgentStreamObserver(
            AgentStreamResponseObserver responseObserver,
            AgentStreamChunkAssembler chunkAssembler,
            AgentStreamTerminalHandler terminalHandler,
            AtomicBoolean terminated,
            AtomicLong chunkCount,
            AtomicLong firstChunkMillis,
            long startedAt) {
        this.responseObserver = responseObserver;
        this.chunkAssembler = chunkAssembler;
        this.terminalHandler = terminalHandler;
        this.terminated = terminated;
        this.chunkCount = chunkCount;
        this.firstChunkMillis = firstChunkMillis;
        this.startedAt = startedAt;
    }

    @Override
    public void onNext(AgentStreamChunk chunk) {
        if (terminated.get()) {
            return;
        }
        firstChunkMillis.compareAndSet(-1L,
                Math.max(System.currentTimeMillis() - startedAt, 0L));
        chunkCount.incrementAndGet();
        responseObserver.onNext(chunkAssembler.assemble(chunk));
    }

    @Override
    public void onComplete(AgentResult finalResult) {
        if (!terminated.compareAndSet(false, true)) {
            return;
        }
        try {
            AgentResponse response = terminalHandler.onComplete(
                    finalResult, statistics());
            responseObserver.onComplete(response);
        } catch (RuntimeException ex) {
            terminalHandler.onError(ex, statistics());
            responseObserver.onError(ex);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        if (!terminated.compareAndSet(false, true)) {
            return;
        }
        terminalHandler.onError(throwable, statistics());
        responseObserver.onError(throwable);
    }

    private StreamStatistics statistics() {
        long firstMillis = firstChunkMillis.get();
        return StreamStatistics.builder()
                .chunkCount(chunkCount.get())
                .firstChunkMillis(firstMillis < 0L ? null : firstMillis)
                .build();
    }
}
