package com.example.agentgateway.application.filteredstream.model;

import com.example.agentgateway.application.dto.AixzChatRequest;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.Map;

/**
 * 单次流式调用的请求上下文。
 *
 * <p>显式携带业务请求和请求头快照，适用于 Reactor 异步执行；不要使用
 * ThreadLocal 保存请求头，否则切换线程后可能丢失或串请求。</p>
 */
@Getter
@Builder(toBuilder = true)
public class FilteredStreamRequestContext {
    public static final String INTENT_ID_HEADER = "intent-id";
    public static final String LEGACY_INTENT_ID_HEADER = "intentid";
    public static final String MESSAGE_ID_HEADER = "messageId";

    private final AixzChatRequest request;
    @Singular(value = "header", ignoreNullCollections = true)
    private final Map<String, String> headers;
    /** 仅在请求通过前置校验、准备调用外部 API 后生成。 */
    private final String messageId;

    /** 请求头已由接口层统一转换为小写，因此这里按小写键读取。 */
    public String getHeader(String name) {
        if (name == null) {
            return null;
        }
        return headers.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    /** 新协议优先使用 intent-id，同时兼容历史 intentId 请求头。 */
    public String getIntentId() {
        String intentId = getHeader(INTENT_ID_HEADER);
        return isBlank(intentId)
                ? getHeader(LEGACY_INTENT_ID_HEADER) : intentId;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
