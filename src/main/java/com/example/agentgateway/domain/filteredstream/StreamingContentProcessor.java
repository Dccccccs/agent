package com.example.agentgateway.domain.filteredstream;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单次流订阅独占的有状态处理器。它不会把可能属于敏感词、卡片标签或 URL
 * 标签的半截内容提前交给下游。
 */
@Slf4j
public class StreamingContentProcessor {
    private static final String URL_PREFIX = "<url";
    private static final String CARD_PREFIX = "<m-card";
    private static final String FUND_ITEM_PREFIX = "<li";
    private static final String FUND_CARD_END_PREFIX = "</m-card";
    private static final String FALLBACK_MARKER = "<fallback />";
    private static final int MAX_STRUCTURED_CONTENT_LENGTH = 8192;
    private static final Pattern CARD_PLACEHOLDER_PATTERN = Pattern.compile(
            "^card_[A-Za-z0-9_-]+$");
    private static final Pattern CARD_TAG_PATTERN = Pattern.compile(
            "^<m-card\\s+id\\s*=\\s*'(card_[A-Za-z0-9_-]+)'\\s*/>$",
            Pattern.DOTALL);
    private static final Pattern FUND_CARD_OPEN_PATTERN = Pattern.compile(
            "^<m-card(?:\\s+id\\s*=\\s*(?:'[^']*'|\"[^\"]*\"))?\\s*>$",
            Pattern.DOTALL);
    private static final Pattern FUND_CARD_END_PATTERN = Pattern.compile(
            "^</m-card\\s*>$", Pattern.DOTALL);
    private static final Pattern LINK_URL_PATTERN = Pattern.compile(
            "^<url\\s+id\\s*=\\s*\"([^\"]+)\"\\s+text\\s*=\\s*\"([^\"]*)\"\\s*/>$",
            Pattern.DOTALL);
    private static final Pattern FUND_ITEM_PATTERN = Pattern.compile(
            "^<li\\s+id\\s*=\\s*(['\"])([^'\"]+)\\1"
                    + "(?:\\s+text\\s*=\\s*(['\"])(.*?)\\3)?\\s*/>$",
            Pattern.DOTALL);

    private final StreamReplacementCatalog catalog;
    private final Map<String, String> sensitiveReplacements;
    private final List<String> sensitiveWords;
    private final StringBuilder sensitiveBuffer = new StringBuilder();
    private final StringBuilder structuredBuffer = new StringBuilder();
    private final List<FundDefinition> currentFundGroup =
            new ArrayList<FundDefinition>();
    /** 已通过 fundList 校验并出现在完整卡片容器中的基金，按正文首次出现顺序去重。 */
    private final Set<String> recommendedFundIds =
            new LinkedHashSet<String>();
    // 只统计首个完整 li 到最后一个完整 li 的接收跨度，
    // 不把首个 li 自身、等待 </m-card>、卡片 API 和下游发送计入耗时。
    private Long firstLiCompletedAtNanos;
    private Long lastLiCompletedAtNanos;
    // 一旦帧尾仍存在不确定前缀，需要连同本帧已处理文本一起暂存；否则前端
    // 会先看到半截原文，后续无法再完成“合并成一帧”的替换要求。
    private final List<ProcessedContentBlock> heldBlocks =
            new ArrayList<ProcessedContentBlock>();
    // 保存真正输出过的替换后全文，未释放的缓存不会提前写入。
    private final StringBuilder transformedAnswer = new StringBuilder();
    // 记录正文中已经成功通过 Metadata 校验的映射。计数基于完整标签解析结果，
    // 因此标签即使被拆分到多个上游帧，也只会统计一次。
    private final Map<String, Integer> linkOccurrenceCounts =
            new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> cardOccurrenceCounts =
            new LinkedHashMap<String, Integer>();
    // 由本次流的首帧初始化，整体保留为 Metadata 对象。
    private StreamMetadata metadata = StreamMetadata.empty();

    private StreamingContentState state = StreamingContentState.TEXT;
    // 包装类型必须显式初始化，避免首次 ensureOpen 时自动拆箱 null。
    private Boolean finished = Boolean.FALSE;
    private FundReference firstFundReference;

    public StreamingContentProcessor(StreamReplacementCatalog catalog) {
        this.catalog = catalog;
        this.sensitiveReplacements = validateAndCopy(catalog.sensitiveReplacements());
        this.sensitiveWords = new ArrayList<String>(sensitiveReplacements.keySet());
        Collections.sort(this.sensitiveWords,
                (left, right) -> Integer.compare(
                        right.length(), left.length()));
    }

    /**
     * 接收一个上游帧。返回空集合表示当前帧需要继续阻塞；返回多个内容块时，调用方
     * 应按顺序分别发送 DATA 帧。
     */
    public List<ProcessedContentBlock> accept(String chunk) {
        ensureOpen();
        String input = chunk == null ? "" : chunk;
        List<ProcessedContentBlock> currentBlocks =
                new ArrayList<ProcessedContentBlock>();
        StringBuilder textOutput = new StringBuilder();
        int urlCountInInputFrame = 0;

        for (int index = 0; index < input.length(); index++) {
            urlCountInInputFrame = processCharacter(input.charAt(index),
                    textOutput, currentBlocks, urlCountInInputFrame);
        }

        flushText(textOutput, currentBlocks);
        appendNormalized(heldBlocks, currentBlocks);
        validateAtMostOneLinkPerTextBlock(heldBlocks);
        if (isWaitingForMoreInput()) {
            // 当前帧存在未决前缀时，不向下游泄露本帧的任何部分。
            return Collections.emptyList();
        }
        return releaseHeldBlocks();
    }

    private int processCharacter(char character,
                                 StringBuilder textOutput,
                                 List<ProcessedContentBlock> currentBlocks,
                                 int urlCountInInputFrame) {
        switch (state) {
            case TEXT:
                processTextCharacter(character, textOutput);
                break;
            case TAG_CANDIDATE:
                processTagCandidateCharacter(character, textOutput);
                break;
            case CARD:
                processCardCharacter(character, textOutput, currentBlocks);
                break;
            case URL:
                return processUrlCharacter(character, textOutput,
                        currentBlocks, urlCountInInputFrame);
            case FUND_ITEM:
                processFundItemCharacter(character, textOutput, currentBlocks);
                break;
            case FUND_CARD_BODY:
                processFundCardBodyCharacter(character);
                break;
            case FUND_CARD_TAG_CANDIDATE:
                processFundCardTagCandidateCharacter(character);
                break;
            case FUND_CARD_END:
                processFundCardEndCharacter(character, currentBlocks);
                break;
            default:
                throw new IllegalStateException("未知流处理状态");
        }
        return urlCountInInputFrame;
    }

    private void processTextCharacter(char character, StringBuilder textOutput) {
        // 结构标记优先于敏感词规则；遇到结构起始符时先结束当前
        // 敏感词判断，再切换到对应的跨帧状态。
        if (character == '<') {
            flushSensitiveLiteral(textOutput);
            state = StreamingContentState.TAG_CANDIDATE;
            structuredBuffer.setLength(0);
            structuredBuffer.append(character);
        } else {
            feedSensitive(character, textOutput);
        }
    }

    private void processTagCandidateCharacter(char character,
                                              StringBuilder textOutput) {
        structuredBuffer.append(character);
        String candidate = structuredBuffer.toString();
        if (FALLBACK_MARKER.equals(candidate)) {
            // 控制标记和同一上游帧中尚未释放的内容都不能进入 DATA。
            throw new StreamFallbackRequestedException();
        } else if (URL_PREFIX.equals(candidate)) {
            state = StreamingContentState.URL;
        } else if (CARD_PREFIX.equals(candidate)) {
            state = StreamingContentState.CARD;
        } else if (FUND_ITEM_PREFIX.equals(candidate)) {
            throw fail("<li> 标签必须位于 <m-card> 容器内");
        } else if (FUND_CARD_END_PREFIX.equals(candidate)) {
            throw fail("出现没有开始标签的 </m-card>");
        } else if (!URL_PREFIX.startsWith(candidate)
                && !CARD_PREFIX.startsWith(candidate)
                && !FUND_ITEM_PREFIX.startsWith(candidate)
                && !FUND_CARD_END_PREFIX.startsWith(candidate)
                && !FALLBACK_MARKER.startsWith(candidate)) {
            // 例如 <Houseview> 不是受控标签，把缓存恢复为普通文本。
            state = StreamingContentState.TEXT;
            feedOrdinaryText(candidate, textOutput);
            structuredBuffer.setLength(0);
        }
    }

    private void processCardCharacter(char character,
                                      StringBuilder textOutput,
                                      List<ProcessedContentBlock> currentBlocks) {
        structuredBuffer.append(character);
        ensureStructuredLength();
        if (character != '>') {
            return;
        }
        String tag = structuredBuffer.toString();
        flushText(textOutput, currentBlocks);
        if (endsWith(structuredBuffer, "/>")) {
            currentBlocks.add(resolveCard(tag));
            state = StreamingContentState.TEXT;
        } else if (FUND_CARD_OPEN_PATTERN.matcher(tag).matches()) {
            currentFundGroup.clear();
            firstLiCompletedAtNanos = null;
            lastLiCompletedAtNanos = null;
            state = StreamingContentState.FUND_CARD_BODY;
        } else {
            throw fail("非法 <m-card> 开始标签: " + tag);
        }
        structuredBuffer.setLength(0);
    }

    private int processUrlCharacter(char character,
                                    StringBuilder textOutput,
                                    List<ProcessedContentBlock> currentBlocks,
                                    int urlCountInInputFrame) {
        structuredBuffer.append(character);
        ensureStructuredLength();
        if (!endsWith(structuredBuffer, "/>")) {
            return urlCountInInputFrame;
        }
        int completedUrlCount = urlCountInInputFrame + 1;
        if (completedUrlCount > 1) {
            throw fail("同一个上游帧中不允许包含多个 <url> 标签");
        }
        flushText(textOutput, currentBlocks);
        currentBlocks.add(resolveLink(structuredBuffer.toString()));
        structuredBuffer.setLength(0);
        state = StreamingContentState.TEXT;
        return completedUrlCount;
    }

    private void processFundItemCharacter(char character,
                                          StringBuilder textOutput,
                                          List<ProcessedContentBlock> currentBlocks) {
        structuredBuffer.append(character);
        ensureStructuredLength();
        if (endsWith(structuredBuffer, "/>")) {
            long completedAtNanos = System.nanoTime();
            flushText(textOutput, currentBlocks);
            addFundToCurrentGroup(structuredBuffer.toString());
            if (firstLiCompletedAtNanos == null) {
                firstLiCompletedAtNanos = completedAtNanos;
            }
            lastLiCompletedAtNanos = completedAtNanos;
            structuredBuffer.setLength(0);
            state = StreamingContentState.FUND_CARD_BODY;
        }
    }

    private void processFundCardBodyCharacter(char character) {
        if (character == '<') {
            structuredBuffer.setLength(0);
            structuredBuffer.append(character);
            state = StreamingContentState.FUND_CARD_TAG_CANDIDATE;
        } else if (!Character.isWhitespace(character)) {
            throw fail("<m-card> 容器内只允许 <li> 标签和空白字符");
        }
    }

    private void processFundCardTagCandidateCharacter(char character) {
        structuredBuffer.append(character);
        ensureStructuredLength();
        String candidate = structuredBuffer.toString();
        if (FALLBACK_MARKER.equals(candidate)) {
            // 容器内的兜底标记也优先于卡片生成。
            throw new StreamFallbackRequestedException();
        } else if (FUND_ITEM_PREFIX.equals(candidate)) {
            state = StreamingContentState.FUND_ITEM;
        } else if (FUND_CARD_END_PREFIX.equals(candidate)) {
            state = StreamingContentState.FUND_CARD_END;
        } else if (!FUND_ITEM_PREFIX.startsWith(candidate)
                && !FUND_CARD_END_PREFIX.startsWith(candidate)
                && !FALLBACK_MARKER.startsWith(candidate)) {
            throw fail("<m-card> 容器内存在非法标签: " + candidate);
        }
    }

    private void processFundCardEndCharacter(
            char character, List<ProcessedContentBlock> currentBlocks) {
        structuredBuffer.append(character);
        ensureStructuredLength();
        if (character == '>') {
            String tag = structuredBuffer.toString();
            if (!FUND_CARD_END_PATTERN.matcher(tag).matches()) {
                throw fail("非法 </m-card> 结束标签: " + tag);
            }
            flushFundGroup(currentBlocks);
            structuredBuffer.setLength(0);
            state = StreamingContentState.TEXT;
        }
    }

    /**
     * 上游正常结束时冲刷安全的普通文本；未闭合的卡片或 URL 被视为协议错误。
     */
    public List<ProcessedContentBlock> finish() {
        ensureOpen();
        if (state == StreamingContentState.CARD) {
            throw fail("流结束时存在未闭合的 <m-card> 标签");
        }
        if (state == StreamingContentState.URL) {
            throw fail("流结束时存在未闭合的 <url> 标签");
        }
        if (state == StreamingContentState.FUND_ITEM
                || state == StreamingContentState.FUND_CARD_BODY
                || state == StreamingContentState.FUND_CARD_TAG_CANDIDATE
                || state == StreamingContentState.FUND_CARD_END) {
            throw fail("流结束时存在未闭合的 <m-card> 基金容器");
        }

        StringBuilder textOutput = new StringBuilder();
        List<ProcessedContentBlock> finalBlocks =
                new ArrayList<ProcessedContentBlock>();
        if (state == StreamingContentState.TAG_CANDIDATE) {
            feedOrdinaryText(structuredBuffer.toString(), textOutput);
            structuredBuffer.setLength(0);
            state = StreamingContentState.TEXT;
        }
        flushSensitiveLiteral(textOutput);
        flushText(textOutput, finalBlocks);
        appendNormalized(heldBlocks, finalBlocks);
        validateAtMostOneLinkPerTextBlock(heldBlocks);
        // 数量不足只有在上游正常结束后才能确定。必须在释放最后一批缓存前
        // 校验，避免把尚未通过完整性检查的尾部内容发送给下游。
        validateMappingOccurrenceCompleteness();
        finished = Boolean.TRUE;
        return releaseHeldBlocks();
    }

    public String getTransformedAnswer() {
        return transformedAnswer.toString();
    }

    /** 注册完整的首帧业务对象，且只允许在正文处理前调用。 */
    public void registerMetadata(StreamMetadata metadataContent) {
        ensureOpen();
        if (state != StreamingContentState.TEXT || transformedAnswer.length() > 0
                || !heldBlocks.isEmpty()) {
            throw new IllegalStateException("Metadata 必须在处理正文前注册");
        }
        metadata = validateAndCopyMetadata(metadataContent);
    }

    /** 返回正文里第一个成功解析的 fundId URL。 */
    public Optional<FundReference> getFirstFundReference() {
        return Optional.ofNullable(firstFundReference);
    }

    public List<String> getRecommendedFundIds() {
        return Collections.unmodifiableList(
                new ArrayList<String>(recommendedFundIds));
    }

    private ProcessedContentBlock resolveCard(String tag) {
        // 模型只允许输出 <m-card id='card_xxx' />；具体卡片数据必须来自
        // 本次请求的 Metadata.cardMapping。
        Matcher matcher = CARD_TAG_PATTERN.matcher(tag);
        if (!matcher.matches()) {
            throw fail("非法 <m-card> 标签: " + tag);
        }
        String placeholder = matcher.group(1);
        Optional<CardDefinition> optionalCard = metadata.findCard(placeholder);
        if (!optionalCard.isPresent()) {
            throw fail("未找到卡片占位符对应的数据: " + placeholder);
        }
        recordOccurrence(cardOccurrenceCounts, placeholder,
                "正文中重复出现卡片占位符: ");
        CardDefinition card = optionalCard.get();
        String content = "<m-card card-id=\"" + escapeAttribute(card.getCardId())
                + "\" card-name=\"" + escapeAttribute(card.getCardName())
                + "\" card-description=\"" + escapeAttribute(card.getCardDescription())
                + " \" />";
        return ProcessedContentBlock.card(replaceCompleteText(content), card);
    }

    private ProcessedContentBlock resolveLink(String tag) {
        Matcher linkMatcher = LINK_URL_PATTERN.matcher(tag);
        if (!linkMatcher.matches()) {
            throw fail("非法 <url> 标签: " + tag);
        }
        String lookupId = linkMatcher.group(1);
        Optional<LinkDefinition> optionalLink = metadata.findLink(lookupId);
        if (!optionalLink.isPresent()) {
            throw fail("URL 中的基金不在 Metadata.linkMapping 中, id="
                    + lookupId);
        }
        String canonicalFundName = optionalLink.get().getLinkContent();
        if (canonicalFundName == null || canonicalFundName.trim().isEmpty()) {
            throw fail("Metadata.linkMapping 中的 linkContent 为空, linkId="
                    + lookupId);
        }
        recordOccurrence(linkOccurrenceCounts, lookupId,
                "正文中重复出现 linkId: ");
        if (firstFundReference == null) {
            firstFundReference = FundReference.builder()
                    .fundId(lookupId)
                    .text(canonicalFundName)
                    .build();
        }
        LinkDefinition link = optionalLink.get();
        String outputTag = "<url id=\"" + escapeAttribute(lookupId)
                + "\" text=\"" + escapeAttribute(canonicalFundName) + "\"/>";
        return ProcessedContentBlock.link(replaceCompleteText(outputTag), link);
    }

    private void addFundToCurrentGroup(String tag) {
        Matcher matcher = FUND_ITEM_PATTERN.matcher(tag);
        if (!matcher.matches()) {
            throw fail("非法 <li> 标签: " + tag);
        }
        String fundId = matcher.group(2);
        Optional<FundDefinition> fund = metadata.findFund(fundId);
        if (!fund.isPresent()) {
            throw fail("li 中的基金不在 Metadata.fundList 中, fundId=" + fundId);
        }
        for (FundDefinition existing : currentFundGroup) {
            if (existing.getFundId().equals(fundId)) {
                return;
            }
        }
        FundDefinition metadataFund = fund.get();
        String text = matcher.group(4);
        // 没有 text 属性时保留首帧 fundDesc；只要模型显式输出
        // text（包括空字符串），就以它覆盖本次卡片请求的 fundDesc。
        if (text == null) {
            currentFundGroup.add(metadataFund);
        } else {
            currentFundGroup.add(FundDefinition.builder()
                    .fundId(metadataFund.getFundId())
                    .fundName(metadataFund.getFundName())
                    .fundDesc(text)
                    .build());
        }
    }

    private void flushFundGroup(List<ProcessedContentBlock> blocks) {
        if (currentFundGroup.isEmpty()) {
            throw fail("基金卡片组不能为空");
        }
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                lastLiCompletedAtNanos - firstLiCompletedAtNanos);
        blocks.add(ProcessedContentBlock.fundCardGroup(
                FundCardSource.builder()
                        .subtitle(metadata.getFundList().getSubtitle())
                        .list(currentFundGroup)
                        .build()));
        for (FundDefinition fund : currentFundGroup) {
            recommendedFundIds.add(fund.getFundId());
        }
        log.info("基金卡片 li 列表接收耗时, elapsedMillis={}", elapsedMillis);
        currentFundGroup.clear();
        firstLiCompletedAtNanos = null;
        lastLiCompletedAtNanos = null;
    }

    private void feedOrdinaryText(String text, StringBuilder output) {
        for (int index = 0; index < text.length(); index++) {
            feedSensitive(text.charAt(index), output);
        }
    }

    private void feedSensitive(char character, StringBuilder output) {
        sensitiveBuffer.append(character);
        drainSensitive(output, false);
    }

    private void flushSensitiveLiteral(StringBuilder output) {
        drainSensitive(output, true);
    }

    private void drainSensitive(StringBuilder output, boolean endOfTextSegment) {
        while (sensitiveBuffer.length() > 0) {
            String buffered = sensitiveBuffer.toString();
            String longestMatch = longestWordPrefix(buffered);
            boolean canGrow = hasLongerWordStartingWith(buffered);

            if (!endOfTextSegment && canGrow) {
                // 当前缓存既能匹配短词又可能继续成长为长词，必须等待下一帧，
                // 例如“极端”需要等待确认是“极端高”还是“极端水平”。
                return;
            }
            if (longestMatch != null) {
                output.append(sensitiveReplacements.get(longestMatch));
                sensitiveBuffer.delete(0, longestMatch.length());
                continue;
            }
            if (!endOfTextSegment && isPrefixOfSensitiveWord(buffered)) {
                // 仅是敏感词前缀时同样不能提前输出。
                return;
            }
            output.append(sensitiveBuffer.charAt(0));
            sensitiveBuffer.deleteCharAt(0);
        }
    }

    private String longestWordPrefix(String value) {
        for (String word : sensitiveWords) {
            if (value.startsWith(word)) {
                return word;
            }
        }
        return null;
    }

    private boolean hasLongerWordStartingWith(String value) {
        for (String word : sensitiveWords) {
            if (word.length() > value.length() && word.startsWith(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPrefixOfSensitiveWord(String value) {
        for (String word : sensitiveWords) {
            if (word.startsWith(value)) {
                return true;
            }
        }
        return false;
    }

    private String replaceCompleteText(String text) {
        StringBuilder output = new StringBuilder();
        StringBuilder buffer = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            buffer.append(text.charAt(index));
            drainStandaloneSensitive(buffer, output, false);
        }
        drainStandaloneSensitive(buffer, output, true);
        return output.toString();
    }

    private void drainStandaloneSensitive(StringBuilder buffer,
                                          StringBuilder output,
                                          boolean endOfInput) {
        while (buffer.length() > 0) {
            String value = buffer.toString();
            String longestMatch = longestWordPrefix(value);
            if (!endOfInput && hasLongerWordStartingWith(value)) {
                return;
            }
            if (longestMatch != null) {
                output.append(sensitiveReplacements.get(longestMatch));
                buffer.delete(0, longestMatch.length());
            } else if (!endOfInput && isPrefixOfSensitiveWord(value)) {
                return;
            } else {
                output.append(buffer.charAt(0));
                buffer.deleteCharAt(0);
            }
        }
    }

    private void flushText(StringBuilder output,
                           List<ProcessedContentBlock> blocks) {
        if (output.length() == 0) {
            return;
        }
        blocks.add(ProcessedContentBlock.text(output.toString()));
        output.setLength(0);
    }

    private void appendNormalized(List<ProcessedContentBlock> target,
                                  List<ProcessedContentBlock> source) {
        for (ProcessedContentBlock block : source) {
            if (!block.isFundCardGroup()
                    && (block.getContent() == null || block.getContent().isEmpty())) {
                continue;
            }
            int lastIndex = target.size() - 1;
            if (lastIndex >= 0
                    && ProcessedContentBlock.TYPE_TEXT.equals(target.get(lastIndex).getType())
                    && ProcessedContentBlock.TYPE_TEXT.equals(block.getType())) {
                // 相邻普通文本合并，保证跨帧阻塞解除后尽量以一帧输出。
                target.set(lastIndex, target.get(lastIndex).mergeText(block));
            } else {
                target.add(block);
            }
        }
    }

    private List<ProcessedContentBlock> releaseHeldBlocks() {
        List<ProcessedContentBlock> released =
                new ArrayList<ProcessedContentBlock>(heldBlocks);
        heldBlocks.clear();
        for (ProcessedContentBlock block : released) {
            // 只有实际释放给下游的内容才属于最终答案。
            if (!block.isFundCardGroup()) {
                transformedAnswer.append(block.getContent());
            }
        }
        return Collections.unmodifiableList(released);
    }

    private boolean isWaitingForMoreInput() {
        return state != StreamingContentState.TEXT || sensitiveBuffer.length() > 0;
    }

    private void validateAtMostOneLinkPerTextBlock(
            List<ProcessedContentBlock> blocks) {
        for (ProcessedContentBlock block : blocks) {
            if (block.getLinkMapping().size() > 1) {
                throw fail("同一个输出帧中不允许包含多个 <url> 标签");
            }
        }
    }

    /**
     * 校验首帧声明的每一个链接和卡片映射都在最终正文中恰好出现一次。
     *
     * <p>单个标签引用不存在的映射、以及同一映射重复出现，会在标签解析时
     * 立即失败；这里负责流结束后才能确定的“映射缺失”场景。</p>
     */
    private void validateMappingOccurrenceCompleteness() {
        List<String> missingLinkIds = new ArrayList<String>();
        for (LinkDefinition link : metadata.getLinkMapping()) {
            if (!linkOccurrenceCounts.containsKey(link.getLinkId())) {
                missingLinkIds.add(link.getLinkId());
            }
        }
        if (linkOccurrenceCounts.size() != metadata.getLinkMapping().size()
                || !missingLinkIds.isEmpty()) {
            throw fail("正文 linkMapping 数量不一致，期望="
                    + metadata.getLinkMapping().size()
                    + "，实际=" + linkOccurrenceCounts.size()
                    + "，缺失 linkId=" + missingLinkIds);
        }

        List<String> missingCardPlaceholders = new ArrayList<String>();
        for (CardDefinition card : metadata.getCardMapping()) {
            if (!cardOccurrenceCounts.containsKey(card.getCardPlaceHolder())) {
                missingCardPlaceholders.add(card.getCardPlaceHolder());
            }
        }
        if (cardOccurrenceCounts.size() != metadata.getCardMapping().size()
                || !missingCardPlaceholders.isEmpty()) {
            throw fail("正文 cardMapping 数量不一致，期望="
                    + metadata.getCardMapping().size()
                    + "，实际=" + cardOccurrenceCounts.size()
                    + "，缺失 cardPlaceHolder=" + missingCardPlaceholders);
        }
    }

    private void recordOccurrence(Map<String, Integer> occurrences,
                                  String key,
                                  String duplicateMessagePrefix) {
        Integer count = occurrences.get(key);
        if (count != null) {
            throw fail(duplicateMessagePrefix + key);
        }
        occurrences.put(key, Integer.valueOf(1));
    }

    private void ensureStructuredLength() {
        if (structuredBuffer.length() > MAX_STRUCTURED_CONTENT_LENGTH) {
            throw fail("结构化内容超过最大允许长度");
        }
    }

    private void ensureOpen() {
        if (Boolean.TRUE.equals(finished)) {
            throw new IllegalStateException("流内容处理器已经结束");
        }
    }

    private StreamContentProcessingException fail(String message) {
        finished = Boolean.TRUE;
        return new StreamContentProcessingException(message);
    }

    private static boolean endsWith(StringBuilder value, String suffix) {
        if (value.length() < suffix.length()) {
            return false;
        }
        int offset = value.length() - suffix.length();
        for (int index = 0; index < suffix.length(); index++) {
            if (value.charAt(offset + index) != suffix.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private static String escapeAttribute(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static Map<String, String> validateAndCopy(Map<String, String> source) {
        if (source == null) {
            throw new IllegalArgumentException("敏感词替换映射不能为空");
        }
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isEmpty()) {
                throw new IllegalArgumentException("敏感词不能为空字符串");
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("敏感词替换值不能为空");
            }
            result.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    private static StreamMetadata validateAndCopyMetadata(StreamMetadata source) {
        if (source == null) {
            throw new IllegalArgumentException("Metadata 不能为空");
        }
        List<LinkDefinition> links = new ArrayList<LinkDefinition>();
        for (LinkDefinition link : source.getLinkMapping()) {
            if (link == null || isBlank(link.getLinkId())) {
                throw new IllegalArgumentException("linkId 不能为空");
            }
            if (isBlank(link.getLinkContent())) {
                throw new IllegalArgumentException(
                        "linkContent 不能为空, linkId=" + link.getLinkId());
            }
            if (containsLink(links, link.getLinkId())) {
                throw new IllegalArgumentException(
                        "Metadata.linkMapping 存在重复 linkId: "
                                + link.getLinkId());
            }
            links.add(LinkDefinition.builder()
                    .linkId(link.getLinkId())
                    .linkContent(link.getLinkContent())
                    .build());
        }

        List<CardDefinition> cards = new ArrayList<CardDefinition>();
        for (CardDefinition card : source.getCardMapping()) {
            String placeholder = card == null ? null : card.getCardPlaceHolder();
            if (placeholder == null
                    || !CARD_PLACEHOLDER_PATTERN.matcher(placeholder).matches()) {
                throw new IllegalArgumentException(
                        "非法 cardPlaceHolder: " + placeholder);
            }
            if (card == null) {
                throw new IllegalArgumentException(
                        "卡片定义不能为空: " + placeholder);
            }
            if (containsCard(cards, placeholder)) {
                throw new IllegalArgumentException(
                        "Metadata.cardMapping 存在重复占位符: " + placeholder);
            }
            if (isBlank(card.getCardId())) {
                throw new IllegalArgumentException(
                        "cardId 不能为空: " + placeholder);
            }
            if (isBlank(card.getCardName())) {
                throw new IllegalArgumentException(
                        "cardName 不能为空: " + placeholder);
            }
            cards.add(CardDefinition.builder()
                    .cardPlaceHolder(placeholder)
                    .cardId(card.getCardId())
                    .cardName(card.getCardName())
                    .cardDescription(nullToEmpty(card.getCardDescription()))
                    .htmlContent(nullToEmpty(card.getHtmlContent()))
                    .build());
        }

        FundCardSource sourceFundList = source.getFundList() == null
                ? FundCardSource.empty() : source.getFundList();
        List<FundDefinition> funds = new ArrayList<FundDefinition>();
        if (sourceFundList.getList() == null) {
            throw new IllegalArgumentException("Metadata.fundList.list 不能为空");
        }
        for (FundDefinition fund : sourceFundList.getList()) {
            if (fund == null || isBlank(fund.getFundId())) {
                throw new IllegalArgumentException("fundId 不能为空");
            }
            if (isBlank(fund.getFundName())) {
                throw new IllegalArgumentException(
                        "fundName 不能为空, fundId=" + fund.getFundId());
            }
            if (containsFund(funds, fund.getFundId())) {
                throw new IllegalArgumentException(
                        "Metadata.fundList 存在重复 fundId: " + fund.getFundId());
            }
            funds.add(FundDefinition.builder()
                    .fundId(fund.getFundId())
                    .fundName(fund.getFundName())
                    .fundDesc(nullToEmpty(fund.getFundDesc()))
                    .build());
        }
        return StreamMetadata.builder()
                .linkMapping(Collections.unmodifiableList(links))
                .cardMapping(Collections.unmodifiableList(cards))
                .fundList(FundCardSource.builder()
                        .subtitle(nullToEmpty(sourceFundList.getSubtitle()))
                        .list(Collections.unmodifiableList(funds))
                        .build())
                .overRiskInfo(source.getOverRiskInfo())
                .fallbackAnswer(source.getFallbackAnswer())
                .status(source.getStatus())
                .build();
    }

    private static boolean containsLink(List<LinkDefinition> links,
                                        String linkId) {
        for (LinkDefinition link : links) {
            if (link.getLinkId().equals(linkId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCard(List<CardDefinition> cards,
                                        String placeholder) {
        for (CardDefinition card : cards) {
            if (card.getCardPlaceHolder().equals(placeholder)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsFund(List<FundDefinition> funds,
                                        String fundId) {
        for (FundDefinition fund : funds) {
            if (fund.getFundId().equals(fundId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
