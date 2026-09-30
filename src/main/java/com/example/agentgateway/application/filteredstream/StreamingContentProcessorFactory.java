package com.example.agentgateway.application.filteredstream;

import com.example.agentgateway.domain.filteredstream.StreamReplacementCatalog;
import com.example.agentgateway.domain.filteredstream.StreamingContentProcessor;

import org.springframework.stereotype.Component;

@Component
public class StreamingContentProcessorFactory {
    private final StreamReplacementCatalog catalog;

    public StreamingContentProcessorFactory(StreamReplacementCatalog catalog) {
        this.catalog = catalog;
    }

    public StreamingContentProcessor create() {
        // StreamingContentProcessor 内部保存跨帧状态，不能注册为单例 Bean；
        // 每次流订阅必须通过工厂创建新实例。
        return new StreamingContentProcessor(catalog);
    }
}
