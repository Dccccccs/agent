package com.example.agentgateway.domain.filteredstream;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 首帧中用于生成主题风险提示的内部数据，不直接透传给下游。 */
@Getter
@Builder
public class ThemeRiskTipInfo {
    private final ThemeRiskUserInfo userInfo;
    private final List<ThemeRiskFundInfo> fundList;
}
