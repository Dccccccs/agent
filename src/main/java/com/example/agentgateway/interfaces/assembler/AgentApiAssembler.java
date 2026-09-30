package com.example.agentgateway.interfaces.assembler;

import com.example.agentgateway.domain.model.AgentGatewayRequest;
import com.example.agentgateway.domain.model.MatchedInfo;
import com.example.agentgateway.domain.model.MutualFundItem;
import com.example.agentgateway.domain.model.Slot;
import com.example.agentgateway.application.dto.AixzChatRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AgentApiAssembler {
    AgentGatewayRequest assemble(AixzChatRequest request);
    @Mapping(target = "mutualFundItem", ignore = true)
    Slot assemble(com.example.agentgateway.application.dto.Slot slot);
    MutualFundItem assemble(
            com.example.agentgateway.application.dto.MutualFundItem item);
    MatchedInfo assemble(
            com.example.agentgateway.application.dto.MatchedInfo matchedInfo);
}
