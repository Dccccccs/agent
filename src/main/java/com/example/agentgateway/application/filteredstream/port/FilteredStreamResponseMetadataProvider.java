package com.example.agentgateway.application.filteredstream.port;

/**
 * 提供独立流式接口建立响应时需要写入响应头的静态元数据。
 */
public interface FilteredStreamResponseMetadataProvider {
    String workflowId();
}
