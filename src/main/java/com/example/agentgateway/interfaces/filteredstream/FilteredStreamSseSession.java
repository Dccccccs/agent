package com.example.agentgateway.interfaces.filteredstream;

import com.example.agentgateway.application.filteredstream.model.FilteredStreamEvent;
import com.example.agentgateway.application.filteredstream.model.FilteredStreamEventType;
import com.example.agentgateway.domain.filteredstream.FallbackMessage;
import com.example.agentgateway.interfaces.filteredstream.assembler.FilteredStreamFrameAssembler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单个客户端连接对应的传输会话，同时持有下游 SseEmitter 和上游 Reactor
 * 订阅，负责将客户端断开信号传播到 WebClient。
 */
@Slf4j
final class FilteredStreamSseSession {
    private final SseEmitter emitter;
    private final FilteredStreamFrameAssembler frameAssembler;
    private final List<FallbackMessage> fallbackMessages;
    private final String recordId;
    private final AtomicReference<Disposable> subscription =
            new AtomicReference<Disposable>();
    private final AtomicBoolean terminated = new AtomicBoolean(false);
    private final AtomicBoolean metadataSent = new AtomicBoolean(false);

    FilteredStreamSseSession(
            SseEmitter emitter,
            FilteredStreamFrameAssembler frameAssembler,
            List<FallbackMessage> fallbackMessages,
            String recordId) {
        this.emitter = emitter;
        this.frameAssembler = frameAssembler;
        this.fallbackMessages = fallbackMessages;
        this.recordId = recordId;
    }

    void registerLifecycleCallbacks() {
        // 超时、正常关闭和网络错误都可能由 Servlet 容器线程触发，
        // cancelFromClient 使用原子状态保证取消动作只执行一次。
        emitter.onTimeout(() -> {
            cancelFromClient();
            emitter.complete();
        });
        emitter.onCompletion(this::cancelFromClient);
        emitter.onError(throwable -> cancelFromClient());
    }

    void subscribe(Flux<FilteredStreamEvent> eventStream) {
        Disposable disposable = eventStream.subscribe(
                this::send,
                this::completeWithUnexpectedError,
                this::completeNormally);

        // subscribe 可能与客户端立即断开并发发生。保存失败或会话已经结束时，
        // 立即 dispose，避免上游 WebClient 请求继续占用连接。
        if (!subscription.compareAndSet(null, disposable)) {
            disposable.dispose();
        }
        if (terminated.get()) {
            disposable.dispose();
        }
    }

    private void send(FilteredStreamEvent event) {
        if (terminated.get()) {
            return;
        }
        try {
            Object payload = frameAssembler.toPayload(event);
            emitter.send(SseEmitter.event().data(
                    payload,
                    MediaType.APPLICATION_JSON));
            if (event.getType() == FilteredStreamEventType.METADATA) {
                metadataSent.set(true);
            }
        } catch (IOException ex) {
            terminateWithWriteFailure(ex);
        }
    }

    private void completeNormally() {
        if (terminated.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    private void completeWithUnexpectedError(Throwable throwable) {
        // 可预期的业务异常已在应用层转换成 FALLBACK 事件；这里仅兜底处理
        // 事件发布、协议映射等未预料的异常。
        if (!terminated.compareAndSet(false, true)) {
            return;
        }
        disposeSubscription();
        log.error("流事件发布出现未预期异常，已转换为兜底替换帧", throwable);
        try {
            if (!metadataSent.get()) {
                emitter.send(SseEmitter.event().data(
                        frameAssembler.toPayload(
                                FilteredStreamEvent.emptyMetadata(
                                        recordId, fallbackMessages)),
                        MediaType.APPLICATION_JSON));
                metadataSent.set(true);
            }
            emitter.send(SseEmitter.event().data(
                    frameAssembler.toPayload(
                            FilteredStreamEvent.fallback()),
                    MediaType.APPLICATION_JSON));
            // 传输层未预料异常也遵守“兜底紧邻 DONE”的终止协议。
            emitter.send(SseEmitter.event().data(
                    frameAssembler.toPayload(FilteredStreamEvent.done()),
                    MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

    private void terminateWithWriteFailure(IOException exception) {
        if (terminated.compareAndSet(false, true)) {
            disposeSubscription();
            emitter.completeWithError(exception);
        }
    }

    private void cancelFromClient() {
        if (terminated.compareAndSet(false, true)) {
            disposeSubscription();
        }
    }

    private void disposeSubscription() {
        Disposable disposable = subscription.get();
        if (disposable != null) {
            // Reactor 的取消信号会沿 Flux 链向上传播，最终取消 WebClient 请求。
            disposable.dispose();
        }
    }
}
