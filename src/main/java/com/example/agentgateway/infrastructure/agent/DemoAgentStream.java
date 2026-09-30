package com.example.agentgateway.infrastructure.agent;

import com.example.agentgateway.domain.agent.result.StreamingChatAgentResult;
import com.example.agentgateway.domain.agent.stream.AgentStream;
import com.example.agentgateway.domain.agent.stream.AgentStreamChunk;
import com.example.agentgateway.domain.agent.stream.AgentStreamObserver;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** 演示环境使用的单订阅流式 Agent 输出。 */
final class DemoAgentStream implements AgentStream {
    private final String msgId;
    private final String question;
    private final Executor executor;
    private final AtomicBoolean subscribed = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    DemoAgentStream(String msgId, String question, Executor executor) {
        this.msgId = msgId;
        this.question = question;
        this.executor = executor;
    }

    @Override
    public void subscribe(final AgentStreamObserver observer) {
        if (!subscribed.compareAndSet(false, true)) {
            observer.onError(new IllegalStateException("流式结果只允许订阅一次"));
            return;
        }
        executor.execute(() -> emit(observer));
    }

    private void emit(AgentStreamObserver observer) {
        String[] chunks = new String[] {
                "已收到问题：",
                question,
                "；这是流式Agent返回的结果。"
        };
        StringBuilder answer = new StringBuilder();
        try {
            for (int index = 0; index < chunks.length; index++) {
                if (cancelled.get()) {
                    return;
                }
                answer.append(chunks[index]);
                observer.onNext(AgentStreamChunk.builder()
                        .sequence(index + 1L)
                        .content(chunks[index])
                        .build());
            }
            if (!cancelled.get()) {
                observer.onComplete(StreamingChatAgentResult.builder()
                        .workflowId("stream-workflow-" + msgId)
                        .answer(answer.toString())
                        .build());
            }
        } catch (RuntimeException ex) {
            if (!cancelled.get()) {
                observer.onError(ex);
            }
        }
    }

    @Override
    public void cancel() {
        cancelled.set(true);
    }
}
