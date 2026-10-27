package com.example.agentgateway.domain.filteredstream;

import lombok.Builder;
import lombok.Getter;

/** 客户风评和风格配置状态。 */
@Getter
@Builder
public class ThemeRiskUserInfo {
    private final String riskLevel;
    private final Boolean isPrivate;
    private final String valueStyleStatus;
    private final String balanceStyleStatus;
    private final String growthStyleStatus;
}
