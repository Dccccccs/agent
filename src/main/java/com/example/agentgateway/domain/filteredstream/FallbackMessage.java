package com.example.agentgateway.domain.filteredstream;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * 对客户端公开的统一兜底回答。
 *
 * <p>该对象随首个 METADATA 帧下发，供客户端在收到后续 FALLBACK
 * 通知时直接展示。</p>
 */
@Getter
@Builder
public class FallbackMessage {
    private final String content;
    @Singular(value = "link", ignoreNullCollections = true)
    private final List<ProcessedLinkMapping> linkMapping;
    @Singular(value = "card", ignoreNullCollections = true)
    private final List<ProcessedCardMapping> cardMapping;
}
