package com.example.agentgateway.infrastructure.filteredstream.fallback;

import com.example.agentgateway.application.filteredstream.port.FilteredStreamFallbackProvider;
import com.example.agentgateway.domain.filteredstream.FallbackMessage;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/** 从应用配置读取并转换结构化流式兜底回答。 */
@Component
public class ConfiguredFilteredStreamFallbackProvider
        implements FilteredStreamFallbackProvider {
    private final List<FallbackMessage> fallbackMessages;

    public ConfiguredFilteredStreamFallbackProvider(
            FallbackMessageProperties properties,
            FallbackMessageConverter converter) {
        // 保持现有 application.yml 单对象配置格式，对应用层统一暴露列表。
        this.fallbackMessages = Collections.singletonList(
                converter.convert(properties));
    }

    @Override
    public List<FallbackMessage> fallbackMessages() {
        return fallbackMessages;
    }
}
