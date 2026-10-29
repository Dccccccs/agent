package com.example.agentgateway.application.filteredstream;

import com.example.agentgateway.application.filteredstream.model.FilteredStreamEvent;
import com.example.agentgateway.application.filteredstream.model.FilteredStreamExecution;
import com.example.agentgateway.application.filteredstream.model.FilteredStreamRequestContext;
import com.example.agentgateway.application.filteredstream.model.RecommendationQuery;
import com.example.agentgateway.application.filteredstream.model.RequestValidationResult;
import com.example.agentgateway.application.filteredstream.model.UpstreamStreamFrame;
import com.example.agentgateway.application.filteredstream.assembler.RecommendationQueryAssembler;
import com.example.agentgateway.application.filteredstream.port.ExternalModelStreamPort;
import com.example.agentgateway.application.filteredstream.port.FilteredStreamFallbackProvider;
import com.example.agentgateway.application.filteredstream.port.FilteredStreamMessageIdGenerator;
import com.example.agentgateway.application.filteredstream.port.FilteredStreamRequestValidator;
import com.example.agentgateway.application.filteredstream.port.FundCardPort;
import com.example.agentgateway.domain.filteredstream.ProcessedContentBlock;
import com.example.agentgateway.domain.filteredstream.FallbackMessage;
import com.example.agentgateway.domain.filteredstream.LinkDefinition;
import com.example.agentgateway.domain.filteredstream.FundReference;
import com.example.agentgateway.domain.filteredstream.OverRiskInfo;
import com.example.agentgateway.domain.filteredstream.ProductRiskInfo;
import com.example.agentgateway.domain.filteredstream.StreamMetadata;
import com.example.agentgateway.domain.filteredstream.StreamFallbackRequestedException;
import com.example.agentgateway.domain.filteredstream.StreamingContentProcessor;
import com.example.agentgateway.application.dto.AixzChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 流过滤用例的应用层编排服务。
 *
 * <p>本类只依赖应用端口和领域处理器，不感知 WebClient、SseEmitter 或接口
 * DTO。完整顺序为：请求校验 -> 上游帧消费 -> 领域规则处理 -> 推荐问 -> DONE。</p>
 */
@Service
@Slf4j
public class FilteredStreamService {
    private final ExternalModelStreamPort externalModelStreamPort;
    private final StreamingContentProcessorFactory processorFactory;
    private final FilteredStreamRequestValidator requestValidator;
    private final FilteredStreamFallbackProvider fallbackProvider;
    private final RecommendationQueryAssembler recommendationQueryAssembler;
    private final FilteredStreamMessageIdGenerator messageIdGenerator;
    private final FundCardPort fundCardPort;

    public FilteredStreamService(
            ExternalModelStreamPort externalModelStreamPort,
            StreamingContentProcessorFactory processorFactory,
            FilteredStreamRequestValidator requestValidator,
            FilteredStreamFallbackProvider fallbackProvider,
            RecommendationQueryAssembler recommendationQueryAssembler,
            FilteredStreamMessageIdGenerator messageIdGenerator,
            FundCardPort fundCardPort) {
        this.externalModelStreamPort = externalModelStreamPort;
        this.processorFactory = processorFactory;
        this.requestValidator = requestValidator;
        this.fallbackProvider = fallbackProvider;
        this.recommendationQueryAssembler = recommendationQueryAssembler;
        this.messageIdGenerator = messageIdGenerator;
        this.fundCardPort = fundCardPort;
    }

    /**
     * 在 Controller 提交响应头之前同步完成前置校验并决定是否调用上游。
     * 只有校验通过时才生成 messageId；返回的事件流仍是冷流，订阅后才
     * 真正发起 WebClient 请求。
     */
    public FilteredStreamExecution prepare(
            FilteredStreamRequestContext requestContext) {
        AixzChatRequest request = requestContext == null
                ? null : requestContext.getRequest();
        String intentId = requestContext == null
                ? null : requestContext.getIntentId();
        try {
            if (!hasRequiredFields(request, intentId)) {
                return fallbackExecution(recordIdOf(request));
            }
            RequestValidationResult validation = requestValidator.validate(request);
            if (validation == null || !validation.isAccepted()) {
                return fallbackExecution(recordIdOf(request));
            }

            String messageId = messageIdGenerator.generate();
            if (isBlank(messageId)) {
                throw new IllegalStateException("messageId 生成结果不能为空");
            }
            FilteredStreamRequestContext invocationContext =
                    requestContext.toBuilder()
                            .messageId(messageId)
                            .build();
            return FilteredStreamExecution.builder()
                    .messageId(messageId)
                    .eventStream(createEventStream(invocationContext))
                    .build();
        } catch (RuntimeException ex) {
            // 此处尚未创建外部 API 调用，因此 messageId 按协议返回空值。
            log.error("外部 API 调用前处理失败，已转换为兜底流", ex);
            return fallbackExecution(recordIdOf(request));
        }
    }

    /** 保留原应用接口；需要读取响应头 messageId 时应使用 prepare。 */
    public Flux<FilteredStreamEvent> execute(
            FilteredStreamRequestContext requestContext) {
        return prepare(requestContext).getEventStream();
    }

    private FilteredStreamExecution fallbackExecution(String recordId) {
        return FilteredStreamExecution.builder()
                .messageId("")
                .eventStream(fallbackThenDone(true, recordId, null))
                .build();
    }

    private Flux<FilteredStreamEvent> createEventStream(
            FilteredStreamRequestContext requestContext) {
        AixzChatRequest request = requestContext.getRequest();
        // 【统一帧处理入口】ExternalModelSseClient 已经将原始 SSE JSON 解码成
        // UpstreamStreamFrame；本方法只负责按顺序编排这些统一帧。
        // 每次订阅都创建独立的处理器和上下文，避免多个用户的跨帧缓存相互污染。
        return Flux.defer(() -> {
            FilteredStreamProcessingContext context =
                    new FilteredStreamProcessingContext(
                    processorFactory.create(), request.getMsgId());
            return externalModelStreamPort.openStream(requestContext)
                    // 只要统一上游帧已经抵达，就属于流式处理阶段；之后的
                    // ERROR 帧、协议异常或内容异常都必须覆盖已输出内容。
                    .doOnNext(frame -> {
                        context.markUpstreamFrameReceived();
                        if (!Boolean.TRUE.equals(frame.getMetadata())
                                && frame.hasContent()) {
                            int frameNumber = context.appendOriginContent(
                                    frame.getContent());
                            log.info("接收上游原始正文帧, recordId={}, frameNumber={}, content={}",
                                    context.getRecordId(), frameNumber,
                                    frame.getContent());
                        }
                    })
                    // status=FALLBACK 的首帧仍要交给 processFrame 输出；随后
                    // takeUntil 立即取消上游订阅，避免模型继续生成正文。
                    .takeUntil(frame -> Boolean.TRUE.equals(frame.getMetadata())
                            && frame.getMetadataContent().requestsFallback())
                    // concatMap 保证上游帧严格串行处理，不能用 flatMap 打乱顺序。
                    // prefetch=1 使卡片 API 调用期间向上游施加背压，避免后续
                    // 模型帧在内存中大量排队。
                    .concatMap(frame -> processFrame(context, frame), 1)
                    // 正常完成时刷出缓存、推荐问和 DONE；主动兜底时只补 DONE。
                    .concatWith(Flux.defer(() -> finishStream(context)))
                    .onErrorResume(StreamFallbackRequestedException.class,
                            ignored -> {
                                // <fallback /> 是模型的控制指令，不属于异常日志。
                                // 终止当前处理链会取消上游订阅；客户端从首帧
                                // fallbackAnswer 取兜底话术，随后接收 FALLBACK/DONE。
                                log.info("上游正文请求兜底, recordId={}",
                                        context.getRecordId());
                                return fallbackThenDone(
                                        !context.isMetadataForwarded(),
                                        context.getRecordId(),
                                        context.getOriginContentOrNull());
                            })
                    .onErrorResume(throwable -> {
                        log.error(context.hasUpstreamFrameReceived()
                                        || throwable instanceof UpstreamFrameException
                                        ? "流式处理中出现异常，已转换为 FALLBACK 帧和 DONE"
                                        : "外部 API 在首帧前调用失败，已转换为 FALLBACK 帧和 DONE",
                                throwable);
                        return fallbackThenDone(!context.isMetadataForwarded(),
                                context.getRecordId(),
                                context.getOriginContentOrNull());
                    });
        });
    }

    private Flux<FilteredStreamEvent> fallbackThenDone(
            boolean prependMetadata,
            String recordId,
            String originContent) {
        List<FilteredStreamEvent> events =
                new ArrayList<FilteredStreamEvent>();
        if (prependMetadata) {
            // 请求校验失败或上游首帧前失败时没有可用 Metadata，仍补发一个
            // 含 recordId 和 fallbackAnswer 的首帧，保证协议顺序统一。
            events.add(FilteredStreamEvent.emptyMetadata(
                    recordId, fallbackProvider.fallbackMessages()));
        }
        events.add(FilteredStreamEvent.fallback());
        // 兜底仅替换对外答案；已收到的模型原始正文仍随 DONE 留存。
        events.add(FilteredStreamEvent.done(originContent));
        return Flux.fromIterable(events);
    }

    private boolean hasRequiredFields(AixzChatRequest request,
                                      String intentId) {
        return request != null
                && !isBlank(request.getQuestion())
                && !isBlank(request.getMsgId())
                && !isBlank(intentId);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String recordIdOf(AixzChatRequest request) {
        return request == null ? "" : request.getMsgId();
    }

    private Flux<FilteredStreamEvent> processFrame(
            FilteredStreamProcessingContext context,
            UpstreamStreamFrame frame) {
        // 【单帧业务分派位置】Metadata 走首帧分支；其他帧的 content 进入
        // StreamingContentProcessor，执行跨帧敏感词、卡片和 URL 处理。
        if (Boolean.TRUE.equals(frame.getMetadata())) {
            validateMetadataPosition(context);
            // 保留强类型首帧对象供后续校验、替换和末帧风险信息筛选；
            // 首帧 JSON 仅交给接口层裁剪并发送。
            context.registerMetadata(frame.getMetadataContent());
            List<FallbackMessage> effectiveFallback =
                    resolveFallbackMessages(context.getMetadata());
            if (context.getMetadata().requestsFallback()) {
                // 首帧先按目标 JSON 对象协议输出，紧接 FALLBACK 通知；
                // finishStream 仅追加 DONE。
                context.markFallbackTerminated();
                return Flux.concat(
                        forwardMetadata(context,
                                FilteredStreamEvent.metadata(
                                        frame.getMetadataJson(),
                                        context.getRecordId(),
                                        effectiveFallback)),
                        Flux.just(FilteredStreamEvent.fallback()));
            }
            // Processor 是单次订阅独占实例，注册的 Metadata
            // 业务数据不会污染其他请求。
            context.getProcessor().registerMetadata(context.getMetadata());
            return forwardMetadata(context, FilteredStreamEvent.metadata(
                    frame.getMetadataJson(), context.getRecordId(),
                    effectiveFallback));
        }

        if (!context.isMetadataForwarded()) {
            // 兼容只返回正文增量、没有 Metadata 的上游：先注册空领域元数据，
            // 并在第一个 DATA 之前补发空 Metadata 首帧。
            context.registerMetadata(StreamMetadata.empty());
            context.getProcessor().registerMetadata(context.getMetadata());
            context.markContentStarted();
            return Flux.concat(
                    forwardMetadata(context,
                            FilteredStreamEvent.emptyMetadata(
                                    context.getRecordId(),
                                    fallbackProvider.fallbackMessages())),
                    eventsForBlocks(context,
                            context.getProcessor().accept(
                            frame.getContent())));
        }

        context.markContentStarted();
        // accept 返回空集合表示当前内容仍可能是半截敏感词、卡片或 URL，
        // 需要继续阻塞；返回多个块时按顺序生成多个 DATA 事件。
        return eventsForBlocks(context,
                context.getProcessor().accept(frame.getContent()));
    }

    /** 上游首帧兜底答案列表优先；缺失或解析无效时使用本地配置。 */
    private List<FallbackMessage> resolveFallbackMessages(
            StreamMetadata metadata) {
        if (metadata != null && metadata.getFallbackAnswer() != null
                && !metadata.getFallbackAnswer().isEmpty()) {
            return metadata.getFallbackAnswer();
        }
        return fallbackProvider.fallbackMessages();
    }

    /** 在事件实际被 Reactor 下发时更新状态，避免“已注册但尚未输出”的误判。 */
    private Flux<FilteredStreamEvent> forwardMetadata(
            FilteredStreamProcessingContext context,
            FilteredStreamEvent metadataEvent) {
        return Flux.just(metadataEvent)
                .doOnNext(ignored -> context.markMetadataForwarded());
    }

    private Flux<FilteredStreamEvent> finishStream(
            FilteredStreamProcessingContext context) {
        if (context.isFallbackTerminated()) {
            return Flux.just(FilteredStreamEvent.done(
                    context.getOriginContentOrNull()));
        }
        if (!context.isMetadataForwarded()) {
            // 上游正常完成但一帧也没有返回，按无首帧异常处理，保证中间
            // 至少存在一帧 DATA/FALLBACK，而不是直接从 METADATA 跳到 DONE。
            return fallbackThenDone(true, context.getRecordId(),
                    context.getOriginContentOrNull());
        }
        List<ProcessedContentBlock> finalBlocks =
                context.getProcessor().finish();
        return eventsForBlocks(context, finalBlocks)
                // 同步卡片请求在阻塞任务线程池中执行；只有最后一批内容（包括卡片）全部输出后，
                // 才能判断是否需要兜底并追加推荐问、DONE。
                .concatWith(Flux.defer(() -> finishAfterContent(context)));
    }

    private Flux<FilteredStreamEvent> finishAfterContent(
            FilteredStreamProcessingContext context) {
        List<FilteredStreamEvent> events = new ArrayList<FilteredStreamEvent>();
        if (!context.hasContentProduced()) {
            // 上游只有 Metadata、没有任何可输出正文时，使用 FALLBACK 补齐
            // METADATA -> DATA/FALLBACK -> DONE 的完整序列。
            events.add(FilteredStreamEvent.fallback());
            events.add(FilteredStreamEvent.done(
                    context.getOriginContentOrNull()));
            return Flux.fromIterable(events);
        }

        // 正常完成后统一输出末帧 Metadata：推荐问与按正文基金筛选的
        // overRiskInfo 放在同一帧，且始终位于纯 DONE 之前。
        List<RecommendationQuery> recommendations =
                buildRecommendations(context);
        OverRiskInfo matchedRiskInfo = buildMatchedRiskInfo(context);
        if (!recommendations.isEmpty() || matchedRiskInfo != null) {
            events.add(FilteredStreamEvent.recommendations(
                    recommendations, matchedRiskInfo));
        }
        events.add(FilteredStreamEvent.done(context.getOriginContent()));
        return Flux.fromIterable(events);
    }

    private OverRiskInfo buildMatchedRiskInfo(
            FilteredStreamProcessingContext context) {
        OverRiskInfo source = context.getMetadata().getOverRiskInfo();
        if (source == null) {
            return null;
        }
        Set<String> referencedIds = new HashSet<String>(
                context.getProcessor().getRecommendedFundIds());
        List<ProductRiskInfo> matched = new ArrayList<ProductRiskInfo>();
        for (ProductRiskInfo product : source.getProductRiskInfo()) {
            if (referencedIds.contains(product.getProductCode())) {
                matched.add(product);
            }
        }
        return OverRiskInfo.builder()
                .userRiskInfo(source.getUserRiskInfo())
                .productRiskInfo(Collections.unmodifiableList(matched))
                .build();
    }

    private List<RecommendationQuery> buildRecommendations(
            FilteredStreamProcessingContext context) {
        List<RecommendationQuery> recommendations =
                new ArrayList<RecommendationQuery>(
                        recommendationQueryAssembler.assemble(
                                context.getMetadata().getSuggestedQuery()));
        RecommendationQuery generated = buildFundRecommendation(context);
        if (generated != null) {
            recommendations.add(generated);
        }
        return recommendations;
    }

    private RecommendationQuery buildFundRecommendation(
            FilteredStreamProcessingContext context) {
        // 使用正文中第一个符合 <url id="..." text="..." />
        // 且通过 Metadata.linkMapping 校验的基金链接。
        Optional<FundReference> reference =
                context.getProcessor().getFirstFundReference();
        if (!reference.isPresent()) {
            return null;
        }
        String fundId = reference.get().getFundId();
        Optional<LinkDefinition> link =
                context.getMetadata().findLink(fundId);
        if (!link.isPresent()) {
            return null;
        }
        String fundName = link.get().getLinkContent();
        // 正常情况下名称来自 Metadata.linkMapping；这里保留领域引用作为防御性回退。
        if (fundName == null || fundName.trim().isEmpty()) {
            fundName = reference.get().getText();
        }
        if (fundName == null || fundName.trim().isEmpty()) {
            return null;
        }
        return new RecommendationQuery("深度解读" + fundName, "null");
    }

    private Flux<FilteredStreamEvent> eventsForBlocks(
            FilteredStreamProcessingContext context,
            List<ProcessedContentBlock> blocks) {
        return Flux.fromIterable(blocks)
                // prefetch=1：等待卡片 API 时不主动积压大量后续内部块。
                .concatMap(this::toContentEvent, 1)
                .doOnNext(ignored -> context.markContentProduced());
    }

    private Mono<FilteredStreamEvent> toContentEvent(
            ProcessedContentBlock block) {
        if (!block.isFundCardGroup()) {
            return Mono.just(FilteredStreamEvent.content(block));
        }
        // RestTemplate 是阻塞调用；放在 boundedElastic 上执行，避免阻塞
        // 上游 WebClient 的事件线程，同时保留 concatMap 的顺序和异常兜底。
        return Mono.fromCallable(() -> fundCardPort.createCard(
                        block.getFundCardSource()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ProcessedContentBlock::generatedFundCard)
                .map(FilteredStreamEvent::content);
    }

    private void validateMetadataPosition(
            FilteredStreamProcessingContext context) {
        if (context.isContentStarted()) {
            throw new FilteredStreamException(
                    "上游元数据帧必须位于所有内容帧之前");
        }
        if (context.isMetadataForwarded()) {
            throw new FilteredStreamException("上游返回了多个元数据帧");
        }
    }

}
