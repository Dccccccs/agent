package com.example.agentgateway.interfaces;

import com.example.agentgateway.application.response.AgentResponse;
import com.example.agentgateway.application.response.stream.AgentStreamChunkResponse;
import com.example.agentgateway.application.response.stream.AgentStreamResponse;
import com.example.agentgateway.application.response.stream.AgentStreamResponseObserver;
import com.example.agentgateway.domain.exception.GatewayErrorCode;
import com.example.agentgateway.domain.exception.GatewayException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** 负责旧 Agent 网关流式响应的 SSE 生命周期与事件发送。 */
final class AgentGatewaySseSession implements AgentStreamResponseObserver {
    private final SseEmitter emitter;
    private final AgentStreamResponse streamResponse;

    AgentGatewaySseSession(SseEmitter emitter,
                           AgentStreamResponse streamResponse) {
        this.emitter = emitter;
        this.streamResponse = streamResponse;
    }

    void start() {
        emitter.onTimeout(() -> {
            streamResponse.cancel();
            emitter.complete();
        });
        emitter.onCompletion(streamResponse::cancel);
        emitter.onError(throwable -> streamResponse.cancel());
        streamResponse.subscribe(this);
    }

    @Override
    public void onNext(AgentStreamChunkResponse chunk) {
        try {
            emitter.send(SseEmitter.event()
                    .name("message")
                    .id(String.valueOf(chunk.getSequence()))
                    .data(chunk));
        } catch (IOException ex) {
            streamResponse.cancel();
            emitter.completeWithError(ex);
        }
    }

    @Override
    public void onComplete(AgentResponse finalResponse) {
        try {
            emitter.send(SseEmitter.event()
                    .name("complete")
                    .data(finalResponse));
            emitter.complete();
        } catch (IOException ex) {
            streamResponse.cancel();
            emitter.completeWithError(ex);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(errorBody(throwable)));
            emitter.complete();
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

    private Map<String, Object> errorBody(Throwable throwable) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        if (throwable instanceof GatewayException) {
            GatewayException gatewayException = (GatewayException) throwable;
            body.put("code", gatewayException.getCode());
            body.put("message", gatewayException.getMessage());
        } else {
            body.put("code", GatewayErrorCode.AGENT_EXECUTION_FAILED.getCode());
            body.put("message",
                    GatewayErrorCode.AGENT_EXECUTION_FAILED.getMessage());
        }
        return body;
    }
}
