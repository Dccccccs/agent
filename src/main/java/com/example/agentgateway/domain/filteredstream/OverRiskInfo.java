package com.example.agentgateway.domain.filteredstream;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 首帧中的风险信息；末帧只保留正文引用的产品。 */
@Getter
@Builder
public class OverRiskInfo {
    private final UserRiskInfo userRiskInfo;
    private final List<ProductRiskInfo> productRiskInfo;
}
