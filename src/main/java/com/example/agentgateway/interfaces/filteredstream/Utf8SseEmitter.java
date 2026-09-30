package com.example.agentgateway.interfaces.filteredstream;

import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;

/** 显式声明 UTF-8，避免不同 Servlet 容器对中文 SSE 使用不同默认编码。 */
final class Utf8SseEmitter extends SseEmitter {
    private static final MediaType STREAM_MEDIA_TYPE = new MediaType(
            "text", "event-stream", StandardCharsets.UTF_8);

    Utf8SseEmitter(Long timeout) {
        super(timeout);
    }

    @Override
    protected void extendResponse(ServerHttpResponse outputMessage) {
        super.extendResponse(outputMessage);
        outputMessage.getHeaders().setContentType(STREAM_MEDIA_TYPE);
    }
}
