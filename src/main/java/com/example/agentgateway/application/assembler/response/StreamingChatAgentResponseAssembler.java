package com.example.agentgateway.application.assembler.response;

import com.example.agentgateway.application.response.AgentResponse;
import com.example.agentgateway.application.response.StreamingChatAgentResponse;
import com.example.agentgateway.domain.agent.result.AgentResult;
import com.example.agentgateway.domain.agent.result.StreamingChatAgentResult;
import com.example.agentgateway.domain.exception.GatewayErrorCode;
import com.example.agentgateway.domain.exception.GatewayException;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface StreamingChatAgentResponseAssembler extends AgentResponseAssembler {
    default String agentId() {
        return "stream-chat-agent";
    }

    StreamingChatAgentResponse assembleResponse(StreamingChatAgentResult result);

    default AgentResponse assemble(AgentResult result) {
        if (!(result instanceof StreamingChatAgentResult)) {
            throw new GatewayException(GatewayErrorCode.INVALID_AGENT_RESULT,
                    "流式对话Agent响应转换类型错误");
        }
        return assembleResponse((StreamingChatAgentResult) result);
    }
}
