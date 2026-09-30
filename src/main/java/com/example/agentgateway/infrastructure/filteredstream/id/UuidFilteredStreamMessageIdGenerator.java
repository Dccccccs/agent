package com.example.agentgateway.infrastructure.filteredstream.id;

import com.example.agentgateway.application.filteredstream.port.FilteredStreamMessageIdGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 使用 UUID 生成外部模型调用的 messageId。 */
@Component
public class UuidFilteredStreamMessageIdGenerator
        implements FilteredStreamMessageIdGenerator {

    @Override
    public String generate() {
        return UUID.randomUUID().toString();
    }
}
