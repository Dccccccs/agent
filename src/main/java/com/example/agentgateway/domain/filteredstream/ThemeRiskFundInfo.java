package com.example.agentgateway.domain.filteredstream;

import lombok.Builder;
import lombok.Getter;

/** themeRiskTipInfo.fundList 中的一只基金。 */
@Getter
@Builder
public class ThemeRiskFundInfo {
    private final String fundId;
    private final String riskLevel;
    private final String fundType;
}
