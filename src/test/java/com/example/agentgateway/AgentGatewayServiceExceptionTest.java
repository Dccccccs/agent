package com.example.agentgateway;

import com.example.agentgateway.application.AgentGatewayService;
import com.example.agentgateway.application.AgentInvocationMonitoringPublisher;
import com.example.agentgateway.application.agent.AgentRegistry;
import com.example.agentgateway.application.assembler.response.AgentResponseAssemblerRegistry;
import com.example.agentgateway.application.assembler.stream.AgentStreamChunkAssembler;
import com.example.agentgateway.application.pipeline.GatewayHandler;
import com.example.agentgateway.domain.exception.GatewayErrorCode;
import com.example.agentgateway.domain.exception.GatewayException;
import com.example.agentgateway.domain.model.AgentGatewayRequest;
import com.example.agentgateway.domain.model.GatewayContext;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentGatewayServiceExceptionTest {
    @Test
    void shouldRethrowTheSameGatewayExceptionInstance() {
        GatewayException expected = new GatewayException(
                GatewayErrorCode.AGENT_EXECUTION_FAILED, "指定Agent执行失败");
        GatewayHandler handler = mock(GatewayHandler.class);
        when(handler.supports(any(GatewayContext.class))).thenReturn(true);
        doThrow(expected).when(handler).handle(any(GatewayContext.class));
        AgentGatewayService service = new AgentGatewayService(
                Collections.singletonList(handler),
                mock(AgentInvocationMonitoringPublisher.class),
                mock(AgentResponseAssemblerRegistry.class),
                mock(AgentRegistry.class),
                mock(AgentStreamChunkAssembler.class));
        AgentGatewayRequest request = AgentGatewayRequest.builder()
                .msgId("r-ex")
                .uid("u-ex")
                .question("异常测试")
                .build();

        GatewayException actual = assertThrows(
                GatewayException.class, () -> service.query(request));

        assertSame(expected, actual);
        assertEquals("AGENT_EXECUTION_FAILED", actual.getCode());
        assertEquals("指定Agent执行失败", actual.getMessage());
    }
}
