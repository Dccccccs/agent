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
    /** 仅在应用层内部流转，不会直接装配为下游 DATA 帧。 */
    public static final String TYPE_FUND_CARD_GROUP = "fund-card-group";

    private final String type;
    private final String content;
    // Mapping 与内容块绑定，保证接口层不会把某个 URL/卡片的附加数据
    // 错配到其他 DATA 帧。
    private final List<ProcessedLinkMapping> linkMapping;
    private final List<ProcessedCardMapping> cardMapping;
    private final List<FundDefinition> fundList;

    public static ProcessedContentBlock text(String content) {
        return ProcessedContentBlock.builder()
                .type(TYPE_TEXT)
                .content(content)
                .linkMapping(Collections.<ProcessedLinkMapping>emptyList())
                .cardMapping(Collections.<ProcessedCardMapping>emptyList())
                .fundList(Collections.<FundDefinition>emptyList())
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
                .fundList(Collections.<FundDefinition>emptyList())
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
                .fundList(Collections.<FundDefinition>emptyList())
                .build();
    }

    public static ProcessedContentBlock fundCardGroup(
            List<FundDefinition> funds) {
        return ProcessedContentBlock.builder()
                .type(TYPE_FUND_CARD_GROUP)
                .content("")
                .linkMapping(Collections.<ProcessedLinkMapping>emptyList())
                .cardMapping(Collections.<ProcessedCardMapping>emptyList())
                .fundList(immutableCopy(funds))
                .build();
    }

    public static ProcessedContentBlock generatedFundCard(FundCard card) {
        List<ProcessedCardMapping> mappings = Collections.singletonList(
                ProcessedCardMapping.builder()
                        .cardId(card.getCardId())
                        .htmlContent(card.getHtmlContent())
                        .build());
        return ProcessedContentBlock.builder()
                .type(TYPE_CARD)
                .content("<m-card id='" + escapeAttribute(card.getCardId())
                        + "' />")
                .linkMapping(Collections.<ProcessedLinkMapping>emptyList())
                .cardMapping(immutableCopy(mappings))
                .fundList(Collections.<FundDefinition>emptyList())
                .build();
    }

    public boolean isFundCardGroup() {
        return TYPE_FUND_CARD_GROUP.equals(type);
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
                .fundList(Collections.<FundDefinition>emptyList())
                .build();
    }

    private static <T> List<T> immutableCopy(List<T> source) {
        // 处理结果发布后不允许再修改，避免异步发送期间出现内容漂移。
        return Collections.unmodifiableList(new ArrayList<T>(source));
    }

    private static String escapeAttribute(String value) {
        return value.replace("&", "&amp;")
                .replace("'", "&#39;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

}
