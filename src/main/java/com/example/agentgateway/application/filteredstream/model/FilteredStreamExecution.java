package com.example.agentgateway.application.filteredstream.model;

import lombok.Builder;
import lombok.Getter;
import reactor.core.publisher.Flux;

/**
 * 一次流式请求的准备结果。
 *
 * <p>在 HTTP 响应头提交前同时提供 messageId 和冷事件流，避免等到
 * WebClient 异步订阅后才生成 messageId，导致响应头无法再修改。</p>
 */
@Getter
@Builder
public class FilteredStreamExecution {
    /** 未调用外部 API 时为空字符串。 */
    private final String messageId;
    private final Flux<FilteredStreamEvent> eventStream;
}
