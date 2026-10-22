package com.example.agentgateway.application.filteredstream.port;

import com.example.agentgateway.domain.filteredstream.FundCard;
import com.example.agentgateway.domain.filteredstream.FundCardSource;

/** 为正文中连续出现的一组基金同步生成一张组合卡片。 */
public interface FundCardPort {
    FundCard createCard(FundCardSource source);
}
