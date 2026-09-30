package com.example.agentgateway.application.filteredstream.port;

import com.example.agentgateway.domain.filteredstream.FallbackMessage;

import java.util.List;

/**
 * 提供首帧预下发的统一兜底回答。
 * 具体内容和映射由基础设施层从配置中读取，应用层不感知配置来源。
 */
public interface FilteredStreamFallbackProvider {
    List<FallbackMessage> fallbackMessages();
}
