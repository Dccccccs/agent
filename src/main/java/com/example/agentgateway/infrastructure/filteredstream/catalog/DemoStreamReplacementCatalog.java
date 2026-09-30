package com.example.agentgateway.infrastructure.filteredstream.catalog;

import com.example.agentgateway.domain.filteredstream.StreamReplacementCatalog;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地敏感词演示数据源。基金和卡片都改为使用每次请求
 * Metadata 中的数据，避免跨请求共享业务数据。
 */
@Component
public class DemoStreamReplacementCatalog implements StreamReplacementCatalog {
    private final Map<String, String> sensitiveReplacements;

    public DemoStreamReplacementCatalog() {
        Map<String, String> replacements = new LinkedHashMap<String, String>();
        replacements.put("极端水平", "超出常规水平");
        replacements.put("极端高", "较高");
        replacements.put("极度", "非常");
        sensitiveReplacements = Collections.unmodifiableMap(replacements);

    }

    @Override
    public Map<String, String> sensitiveReplacements() {
        return sensitiveReplacements;
    }

}
