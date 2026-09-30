package com.example.agentgateway.domain.filteredstream;

import java.util.Map;

/**
 * 流内容替换所需的静态外部数据。基金和卡片则从每次请求的
 * Metadata 中提取，不放在 Spring 单例目录中。
 */
public interface StreamReplacementCatalog {
    /** 返回“敏感词 -> 替换文本”映射，处理器会按最长词优先匹配。 */
    Map<String, String> sensitiveReplacements();

}
