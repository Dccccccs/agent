package com.example.agentgateway.application.response.stream;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StreamStatistics {
    private final long chunkCount;
    private final Long firstChunkMillis;
}
