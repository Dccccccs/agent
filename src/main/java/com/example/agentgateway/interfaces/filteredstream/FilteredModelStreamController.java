package com.example.agentgateway.interfaces.filteredstream;

import com.example.agentgateway.application.filteredstream.FilteredStreamService;
import com.example.agentgateway.application.filteredstream.model.FilteredStreamExecution;
import com.example.agentgateway.application.filteredstream.model.FilteredStreamRequestContext;
import com.example.agentgateway.application.filteredstream.port.FilteredStreamFallbackProvider;
import com.example.agentgateway.application.filteredstream.port.FilteredStreamResponseMetadataProvider;
import com.example.agentgateway.application.dto.AixzChatRequest;
import com.example.agentgateway.interfaces.filteredstream.assembler.FilteredStreamFrameAssembler;
import com.example.agentgateway.interfaces.filteredstream.assembler.FilteredStreamRequestContextAssembler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 流过滤功能的接口层入口。
 *
 * <p>Controller 不参与敏感词、卡片、链接和推荐问判断，只负责：</p>
 * <ol>
 *     <li>接收请求并交给 Service 校验；</li>
 *     <li>把 Service 的事件流写入 SSE，并维护连接生命周期。</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/model-stream")
public class FilteredModelStreamController {
    public static final String INTENT_ID_HEADER =
            FilteredStreamRequestContext.INTENT_ID_HEADER;
    public static final String WORKFLOW_ID_HEADER = "workFlowId";
    public static final String MESSAGE_ID_HEADER =
            FilteredStreamRequestContext.MESSAGE_ID_HEADER;
    private static final long STREAM_TIMEOUT_MILLIS = 60_000L;

    private final FilteredStreamService filteredStreamService;
    private final FilteredStreamFrameAssembler frameAssembler;
    private final FilteredStreamFallbackProvider fallbackProvider;
    private final FilteredStreamResponseMetadataProvider responseMetadataProvider;
    private final FilteredStreamRequestContextAssembler requestContextAssembler;

    public FilteredModelStreamController(
            FilteredStreamService filteredStreamService,
            FilteredStreamFrameAssembler frameAssembler,
            FilteredStreamFallbackProvider fallbackProvider,
            FilteredStreamResponseMetadataProvider responseMetadataProvider,
            FilteredStreamRequestContextAssembler requestContextAssembler) {
        this.filteredStreamService = filteredStreamService;
        this.frameAssembler = frameAssembler;
        this.fallbackProvider = fallbackProvider;
        this.responseMetadataProvider = responseMetadataProvider;
        this.requestContextAssembler = requestContextAssembler;
    }

    @PostMapping(value = "/filtered-query",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> filteredQuery(
            @RequestHeader HttpHeaders requestHeaders,
            @RequestBody AixzChatRequest request) {
        FilteredStreamRequestContext requestContext =
                requestContextAssembler.assemble(request, requestHeaders);
        // Service 在响应头提交前完成前置校验：只有确定调用外部 API 时
        // execution 才携带 messageId，前置兜底时该值为空字符串。
        FilteredStreamExecution execution =
                filteredStreamService.prepare(requestContext);
        // 该端点始终返回 SSE，校验拒绝也不再切换为普通 JSON。
        SseEmitter emitter = new Utf8SseEmitter(STREAM_TIMEOUT_MILLIS);
        FilteredStreamSseSession session = new FilteredStreamSseSession(
                emitter, frameAssembler, fallbackProvider.fallbackMessages(),
                request.getMsgId());
        session.registerLifecycleCallbacks();
        session.subscribe(execution.getEventStream());
        // 响应头随 HTTP 响应立即返回，不依赖外部模型何时产生首帧。
        return ResponseEntity.ok()
                .header(WORKFLOW_ID_HEADER,
                        responseMetadataProvider.workflowId())
                .header(MESSAGE_ID_HEADER, execution.getMessageId())
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }

}
