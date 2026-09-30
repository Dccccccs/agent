package com.example.agentgateway.domain.filteredstream;

import lombok.Builder;
import lombok.Getter;

/** 模型正文中第一个通过 Metadata.linkMapping 校验的基金链接。 */
@Getter
@Builder
public class FundReference {
    private final String fundId;
    private final String text;
}
