package com.example.agentgateway.application.filteredstream.port;

/** 为一次实际的外部模型调用生成关联 ID。 */
public interface FilteredStreamMessageIdGenerator {
    String generate();
}
