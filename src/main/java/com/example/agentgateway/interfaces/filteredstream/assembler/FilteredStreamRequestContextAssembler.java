package com.example.agentgateway.interfaces.filteredstream.assembler;

import com.example.agentgateway.application.dto.AixzChatRequest;
import com.example.agentgateway.application.filteredstream.model.FilteredStreamRequestContext;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 将 HTTP 请求及请求头转换为与 Web 框架无关的应用层请求上下文。 */
@Component
public class FilteredStreamRequestContextAssembler {

    public FilteredStreamRequestContext assemble(
            AixzChatRequest request,
            HttpHeaders httpHeaders) {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        if (httpHeaders != null) {
            httpHeaders.forEach((name, values) -> {
                if (values != null && !values.isEmpty()) {
                    headers.put(name.toLowerCase(Locale.ROOT), values.get(0));
                }
            });
        }
        return FilteredStreamRequestContext.builder()
                .request(request)
                .headers(Collections.unmodifiableMap(headers))
                // 尚未调用外部 API，此阶段的 messageId 按协议为空。
                .messageId("")
                .build();
    }
}
