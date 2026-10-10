package com.example.agentgateway.domain.filteredstream;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;
import java.util.Optional;

/**
 * 首帧 Metadata 中与流内容处理有关的强类型对象。
 *
 * <p>保留 linkMapping 和 cardMapping 的列表语义，不将首帧业务
 * 数据对外暴露成 Map。列表在构造时作防御性拷贝，适合在
 * 异步流中安全传递。</p>
 */
@Getter
@Builder
public class StreamMetadata {
    /** 首帧 content.linkMapping 对象列表。 */
    @Singular(value = "link", ignoreNullCollections = true)
    private final List<LinkDefinition> linkMapping;
    @Singular(value = "card", ignoreNullCollections = true)
    private final List<CardDefinition> cardMapping;
    /** 首帧 content.fundList 中可供正文 li 标签引用的基金。 */
    @Singular(value = "fund", ignoreNullCollections = true)
    private final List<FundDefinition> fundList;
    /** 首帧暂存的推荐问，流结束前与接口动态构建的推荐问合并输出。 */
    @Singular(value = "suggestion", ignoreNullCollections = true)
    private final List<SuggestedQueryDefinition> suggestedQuery;
    /** 上游首帧提供的结构化兜底答案块列表。 */
    @Singular(value = "fallback", ignoreNullCollections = true)
    private final List<FallbackMessage> fallbackAnswer;
    /** 上游首帧 content.status，值为 NORMAL 或 FALLBACK。 */
    private final MetadataStatus status;

    public static StreamMetadata empty() {
        return StreamMetadata.builder()
                .status(MetadataStatus.NORMAL)
                .build();
    }

    public boolean requestsFallback() {
        return status == MetadataStatus.FALLBACK;
    }

    public Optional<LinkDefinition> findLink(String linkId) {
        for (LinkDefinition link : linkMapping) {
            if (link.getLinkId().equals(linkId)) {
                return Optional.of(link);
            }
        }
        return Optional.empty();
    }

    public Optional<CardDefinition> findCard(String placeholder) {
        for (CardDefinition card : cardMapping) {
            if (card.getCardPlaceHolder().equals(placeholder)) {
                return Optional.of(card);
            }
        }
        return Optional.empty();
    }

    public Optional<FundDefinition> findFund(String fundId) {
        for (FundDefinition fund : fundList) {
            if (fund.getFundId().equals(fundId)) {
                return Optional.of(fund);
            }
        }
        return Optional.empty();
    }

}
