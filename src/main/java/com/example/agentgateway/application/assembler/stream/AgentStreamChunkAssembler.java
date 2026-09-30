package com.example.agentgateway.application.assembler.stream;

import com.example.agentgateway.application.response.stream.AgentStreamChunkResponse;
import com.example.agentgateway.domain.agent.stream.AgentStreamChunk;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AgentStreamChunkAssembler {
    AgentStreamChunkResponse assemble(AgentStreamChunk chunk);
}
