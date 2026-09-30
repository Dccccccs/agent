package com.example.agentgateway;

import com.example.agentgateway.application.dto.AixzChatRequest;
import com.example.agentgateway.application.filteredstream.model.FilteredStreamRequestContext;
import com.example.agentgateway.infrastructure.filteredstream.client.ExternalModelSseClient;
import com.example.agentgateway.infrastructure.filteredstream.client.UpstreamStreamFrameConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExternalModelSseClientTest {
    @Test
    void shouldForwardIntentIdAsUpstreamRequestHeader() {
        AtomicReference<ClientRequest> capturedRequest =
                new AtomicReference<ClientRequest>();
        WebClient.Builder builder = WebClient.builder()
                .exchangeFunction(request -> {
                    capturedRequest.set(request);
                    return Mono.error(new IllegalStateException(
                            "测试在请求发出后终止响应读取"));
                });
        ExternalModelSseClient client = new ExternalModelSseClient(
                builder,
                new UpstreamStreamFrameConverter(new ObjectMapper()),
                "http://external-model.example/stream");
        AixzChatRequest request = new AixzChatRequest();
        request.setMsgId("intent-header-test");
        request.setQuestion("测试问题");

        FilteredStreamRequestContext requestContext =
                FilteredStreamRequestContext.builder()
                        .request(request)
                        .messageId("message-header-test")
                        .headers(Collections.singletonMap(
                                FilteredStreamRequestContext.INTENT_ID_HEADER,
                                "FUND_RECOMMEND_INTENT"))
                        .build();

        client.openStream(requestContext)
                .onErrorResume(throwable -> Flux.empty())
                .blockLast();

        assertNotNull(capturedRequest.get());
        assertEquals("FUND_RECOMMEND_INTENT",
                capturedRequest.get().headers().getFirst("intent-id"));
        assertEquals("message-header-test",
                capturedRequest.get().headers().getFirst("messageId"));
        assertEquals("http://external-model.example/stream",
                capturedRequest.get().url().toString());
    }
}
