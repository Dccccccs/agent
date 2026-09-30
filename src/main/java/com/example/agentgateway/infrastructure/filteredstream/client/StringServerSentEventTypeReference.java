package com.example.agentgateway.infrastructure.filteredstream.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.codec.ServerSentEvent;

/** WebClient 读取字符串 SSE 所需的泛型类型令牌。 */
final class StringServerSentEventTypeReference
        extends ParameterizedTypeReference<ServerSentEvent<String>> {
}
