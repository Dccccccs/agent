package com.example.agentgateway.infrastructure.filteredstream.fallback;

import lombok.Getter;
import lombok.Setter;

/** 兜底回答中的卡片配置项。 */
@Getter
@Setter
public class FallbackCardMappingProperties {
    private String cardId;
    private String htmlContent;
}
