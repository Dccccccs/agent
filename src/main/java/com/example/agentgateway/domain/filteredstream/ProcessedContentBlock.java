package com.example.agentgateway.domain.filteredstream;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Builder
public class ProcessedContentBlock {
    public static final String TYPE_TEXT = "text";
    public static final String TYPE_CARD = "card";

    private final String type;
    private final String content;
    // Mapping 与内容块绑定，保证接口层不会把某个 URL/卡片的附加数据
    // 错配到其他 DATA 帧。
    private final List<ProcessedLinkMapping> linkMapping;
    private final List<ProcessedCardMapping> cardMapping;

    public static ProcessedContentBlock text(String content) {
        return ProcessedContentBlock.builder()
                .type(TYPE_TEXT)
                .content(content)
                .linkMapping(Collections.<ProcessedLinkMapping>emptyList())
                .cardMapping(Collections.<ProcessedCardMapping>emptyList())
                .build();
    }

    public static ProcessedContentBlock link(String content, LinkDefinition link) {
        List<ProcessedLinkMapping> mappings = Collections.singletonList(
                ProcessedLinkMapping.builder()
                        .linkId(link.getLinkId())
                        .linkContent(link.getLinkContent())
                        .build());
        return ProcessedContentBlock.builder()
                .type(TYPE_TEXT)
                .content(content)
                .linkMapping(immutableCopy(mappings))
                .cardMapping(Collections.<ProcessedCardMapping>emptyList())
                .build();
    }

    public static ProcessedContentBlock card(String content, CardDefinition card) {
        List<ProcessedCardMapping> mappings = Collections.singletonList(
                ProcessedCardMapping.builder()
                        .cardId(card.getCardId())
                        .htmlContent(card.getHtmlContent())
                        .build());
        return ProcessedContentBlock.builder()
                .type(TYPE_CARD)
                .content(content)
                .linkMapping(Collections.<ProcessedLinkMapping>emptyList())
                .cardMapping(immutableCopy(mappings))
                .build();
    }

    public ProcessedContentBlock mergeText(ProcessedContentBlock other) {
        // 跨帧阻塞解除时相邻文本需要合成同一输出块，同时合并其链接映射。
        if (!TYPE_TEXT.equals(type) || !TYPE_TEXT.equals(other.type)) {
            throw new IllegalArgumentException("仅文本内容块可以合并");
        }
        List<ProcessedLinkMapping> mappings =
                new ArrayList<ProcessedLinkMapping>(linkMapping);
        mappings.addAll(other.linkMapping);
        return ProcessedContentBlock.builder()
                .type(TYPE_TEXT)
                .content(content + other.content)
                .linkMapping(immutableCopy(mappings))
                .cardMapping(Collections.<ProcessedCardMapping>emptyList())
                .build();
    }

    private static <T> List<T> immutableCopy(List<T> source) {
        // 处理结果发布后不允许再修改，避免异步发送期间出现内容漂移。
        return Collections.unmodifiableList(new ArrayList<T>(source));
    }

}
