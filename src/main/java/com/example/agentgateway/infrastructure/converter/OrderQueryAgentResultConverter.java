package com.example.agentgateway.infrastructure.converter;

import com.example.agentgateway.domain.agent.raw.OrderQueryAgentRawResult;
import com.example.agentgateway.domain.agent.result.AgentResultConverter;
import com.example.agentgateway.domain.agent.result.OrderQueryAgentResult;
import com.example.agentgateway.domain.exception.GatewayErrorCode;
import com.example.agentgateway.domain.exception.GatewayException;
import org.mapstruct.Mapper;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrderQueryAgentResultConverter extends AgentResultConverter<OrderQueryAgentResult> {
    default String agentId() { return "order-agent"; }

    OrderQueryAgentResult toResult(OrderQueryAgentRawResult source);

    @Named("convertOrderRootResult")
    default OrderQueryAgentResult convert(Object rawResult) {
        if (!(rawResult instanceof OrderQueryAgentRawResult)) {
            throw new GatewayException(GatewayErrorCode.INVALID_AGENT_RESULT, "订单Agent返回结果类型错误");
        }
        return toResult((OrderQueryAgentRawResult) rawResult);
    }
}
