package com.example.agentgateway.interfaces.filteredstream;

import com.example.agentgateway.application.filteredstream.model.FilteredStreamEvent;
import com.example.agentgateway.application.filteredstream.port.FilteredStreamFallbackProvider;
import com.example.agentgateway.application.filteredstream.port.FilteredStreamResponseMetadataProvider;
import com.example.agentgateway.interfaces.filteredstream.assembler.FilteredStreamFrameAssembler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.nio.charset.StandardCharsets;

/**
 * 将请求体反序列化、Spring 参数绑定等发生在 Service 之前的
 * 异常，也适配成本接口统一的 SSE METADATA -> FALLBACK -> DONE 协议。
 */
@RestControllerAdvice(assignableTypes = FilteredModelStreamController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FilteredStreamRequestErrorHandler {
    private static final MediaType STREAM_MEDIA_TYPE = new MediaType(
            "text", "event-stream", StandardCharsets.UTF_8);

    private final FilteredStreamFrameAssembler frameAssembler;
    private final FilteredStreamFallbackProvider fallbackProvider;
    private final FilteredStreamResponseMetadataProvider responseMetadataProvider;
    private final ObjectMapper objectMapper;

    public FilteredStreamRequestErrorHandler(
            FilteredStreamFrameAssembler frameAssembler,
            FilteredStreamFallbackProvider fallbackProvider,
            FilteredStreamResponseMetadataProvider responseMetadataProvider,
            ObjectMapper objectMapper) {
        this.frameAssembler = frameAssembler;
        this.fallbackProvider = fallbackProvider;
        this.responseMetadataProvider = responseMetadataProvider;
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class,
            MissingRequestHeaderException.class,
            BindException.class
    })
    public ResponseEntity<String> handleRequestError(Exception ignored)
            throws JsonProcessingException {
        // 请求体无法反序列化时 Controller 尚未进入，无法建立持续
        // SseEmitter；仍然以标准 text/event-stream 编码返回三个 SSE 帧。
        String metadata = objectMapper.writeValueAsString(
                frameAssembler.toPayload(
                        FilteredStreamEvent.emptyMetadata(
                                "", fallbackProvider.fallbackMessages())));
        String fallback = objectMapper.writeValueAsString(
                frameAssembler.toPayload(FilteredStreamEvent.fallback()));
        String done = objectMapper.writeValueAsString(
                frameAssembler.toPayload(FilteredStreamEvent.done()));
        String body = "data:" + metadata + "\n\n"
                + "data:" + fallback + "\n\n"
                + "data:" + done + "\n\n";
        return ResponseEntity.ok()
                .header(FilteredModelStreamController.WORKFLOW_ID_HEADER,
                        responseMetadataProvider.workflowId())
                // 请求绑定失败发生在外部 API 调用之前，messageId 必须为空。
                .header(FilteredModelStreamController.MESSAGE_ID_HEADER, "")
                .contentType(STREAM_MEDIA_TYPE)
                .body(body);
    }
}
