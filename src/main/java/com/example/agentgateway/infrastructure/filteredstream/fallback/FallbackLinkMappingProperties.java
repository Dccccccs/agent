package com.example.agentgateway.infrastructure.filteredstream.fallback;

import lombok.Getter;
import lombok.Setter;

/** 兜底回答中的链接配置项。 */
@Getter
@Setter
public class FallbackLinkMappingProperties {
    private String linkId;
    private String linkContent;
}
