package com.example.agentgateway.domain.filteredstream;

import lombok.Builder;
import lombok.Getter;

/** 已完成校验和替换的链接附加数据。 */
@Getter
@Builder
public class ProcessedLinkMapping {
    private final String linkId;
    private final String linkContent;
}
