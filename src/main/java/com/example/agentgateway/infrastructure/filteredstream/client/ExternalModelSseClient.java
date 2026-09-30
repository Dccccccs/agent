package com.example.agentgateway.infrastructure.filteredstream.client;

import com.example.agentgateway.application.filteredstream.FilteredStreamException;
import com.example.agentgateway.application.filteredstream.model.FilteredStreamRequestContext;
import com.example.agentgateway.application.filteredstream.model.UpstreamStreamFrame;
import com.example.agentgateway.application.filteredstream.port.ExternalModelStreamPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * WebClient 实现的外部模型流适配器，是“调用外部 API 并读取流”的入口。
 *
 * <p>数据依次经过：</p>
 * <ol>
 *     <li>{@link #responseFrames(FilteredStreamRequestContext)} 发起 HTTP POST；</li>
 *     <li>{@link #decodeBody(ClientResponse)} 从响应中逐个取出 SSE data 字符串；</li>
 *     <li>{@link UpstreamStreamFrameConverter#convert(String)} 将厂商帧转换为统一帧；</li>
 *     <li>统一帧通过 {@link ExternalModelStreamPort} 交给应用服务。</li>
 * </ol>
 *
 * <p>本类不执行敏感词、卡片、URL 或推荐问规则，这些属于领域层和应用层。</p>
 */
@Component
public class ExternalModelSseClient implements ExternalModelStreamPort {
    private static final StringServerSentEventTypeReference SSE_TYPE =
            new StringServerSentEventTypeReference();

    private final WebClient webClient;
    private final UpstreamStreamFrameConverter frameConverter;
    private final String streamUrl;

    public ExternalModelSseClient(
            WebClient.Builder webClientBuilder,
            UpstreamStreamFrameConverter frameConverter,
            @Value("${filtered-stream.upstream.url:"
                    + "http://localhost:18080/api/v1/chat/stream}")
            String streamUrl) {
        this.webClient = webClientBuilder.build();
        this.frameConverter = frameConverter;
        this.streamUrl = streamUrl;
    }

    /**
     * 对应用层暴露统一帧流。此处的输入已经不再是原始 JSON 字符串，
     * 而是 {@link UpstreamStreamFrameConverter} 解析后的 UpstreamStreamFrame。
     * 取消返回 Flux 的订阅会沿 Reactor 链取消底层 WebClient 请求。
     */
    @Override
    public Flux<UpstreamStreamFrame> openStream(
            FilteredStreamRequestContext requestContext) {
        return responseFrames(requestContext)
                .map(frameConverter::convert)
                // takeUntil 会保留触发条件的最后一帧，因此带最后一段 content 的
                // END 帧仍会先进入应用层，然后结束上游消费。
                .takeUntil(frame -> Boolean.TRUE.equals(frame.getDone()))
                // 纯 DONE 和空心跳只负责终止/保活，不作为正文事件继续传递。
                .filter(UpstreamStreamFrame::isDeliverable)
                .onErrorMap(this::toFilteredStreamException);
    }

    private Flux<String> responseFrames(
            FilteredStreamRequestContext requestContext) {
        // 【真实外部 API 调用位置】
        // 请求体直接使用 AixzChatRequest；streamUrl 来自
        // filtered-stream.upstream.url 配置。exchangeToFlux 保持响应体流式消费，
        // 不会等待外部模型生成完整答案后再一次性返回。
        return webClient.post()
                .uri(streamUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                // 只显式转发业务白名单参数，避免把 Cookie、Host 等请求头
                // 无差别带到外部模型。新增透传参数时在这里按需增加。
                .header(FilteredStreamRequestContext.INTENT_ID_HEADER,
                        requestContext.getIntentId())
                // application 层只在确定调用外部 API 后生成该值；同一个 ID
                // 同时返回给下游，用于串联上下游调用日志。
                .header(FilteredStreamRequestContext.MESSAGE_ID_HEADER,
                        requestContext.getMessageId())
                .bodyValue(requestContext.getRequest())
                .exchangeToFlux(this::decodeBody);
    }

    private Flux<String> decodeBody(ClientResponse response) {
        if (response.statusCode().isError()) {
            // HTTP 非 2xx 在响应体开始前即可确定，属于“首帧前调用失败”，
            // 应用层会补齐 METADATA、FALLBACK 和 DONE。
            return response.createException()
                    .flatMapMany(exception -> Flux.<String>error(exception));
        }
        MediaType contentType = response.headers().contentType().orElse(null);
        if (contentType != null
                && MediaType.TEXT_EVENT_STREAM.isCompatibleWith(contentType)) {
            // 【原始 SSE 帧读取位置】
            // 对于 "data:{...}\n\n"，这里只取 data 中的字符串 "{...}"；
            // event、id、retry 都是 SSE 控制信息，不属于模型正文。
            return response.bodyToFlux(SSE_TYPE)
                    .flatMap(event -> event.data() == null
                            ? Flux.<String>empty()
                            : Flux.just(event.data()));
        }
        // 兼容某些上游虽然流式返回、却未正确声明 text/event-stream 的情况。
        return response.bodyToFlux(String.class);
    }

    private Throwable toFilteredStreamException(Throwable throwable) {
        if (throwable instanceof FilteredStreamException) {
            return throwable;
        }
        return new FilteredStreamException("上游流式API调用失败", throwable);
    }
}
