package com.example.agentgateway;

import com.example.agentgateway.application.assembler.InvocationMonitoringEventAssembler;
import com.example.agentgateway.application.monitoring.InvocationMonitoringEvent;
import com.example.agentgateway.domain.agent.result.WeatherAgentResult;
import com.example.agentgateway.domain.model.AgentGatewayRequest;
import com.example.agentgateway.domain.model.AgentInfo;
import com.example.agentgateway.domain.model.GatewayContext;
import com.example.agentgateway.domain.model.IntentRecognitionResult;
import com.example.agentgateway.domain.model.MatchedInfo;
import com.example.agentgateway.domain.model.MutualFundItem;
import com.example.agentgateway.domain.model.Slot;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvocationMonitoringEventAssemblerTest {
    private final InvocationMonitoringEventAssembler assembler = Mappers.getMapper(InvocationMonitoringEventAssembler.class);

    @Test
    void shouldFormatRequestAndIntentTimesAndKeepDurationsInMillis() {
        Instant requestTime = Instant.parse("2026-08-10T01:02:03Z");
        Instant responseTime = Instant.parse("2026-08-10T01:02:05Z");
        Instant intentRequestTime = Instant.parse("2026-08-10T01:02:03Z");
        Instant intentResponseTime = Instant.parse("2026-08-10T01:02:04Z");
        AgentGatewayRequest request = AgentGatewayRequest.builder()
                .msgId("r-time")
                .sessionId("s-time")
                .uid("u-time")
                .appId("app-time")
                .question("time test")
                .rewriteQuestion("rewritten time test")
                .slot(Slot.builder()
                        .mutualFund(Collections.singletonList(
                                MutualFundItem.builder()
                                        .start(0)
                                        .end(6)
                                        .key("招商基金")
                                        .matchedInfo(MatchedInfo.builder()
                                                .name("招商基金")
                                                .code("000001")
                                                .fundType("混合型")
                                                .build())
                                        .build()))
                        .build())
                .extraParam(Collections.<String, Object>singletonMap("channel", "app"))
                .build();
        GatewayContext context = GatewayContext.builder().request(request).build();
        context.beginIntentRecognition(intentRequestTime);
        context.completeIntentRecognition(
                IntentRecognitionResult.builder()
                        .intentId("TIME_INTENT")
                        .intentResult(Collections.<String, Object>emptyMap())
                        .build(),
                intentResponseTime,
                1000L);
        context.completeAgentExecution(
                AgentInfo.builder().agentCode("weather-agent").useOffline(0).build(),
                WeatherAgentResult.builder().workflowId("workflow-time-001").build());

        InvocationMonitoringEvent event = assembler.assemble(
                request, context, requestTime, responseTime,
                2000L, true, null, null,
                false, null, null, null);

        assertEquals(assembler.formatTime(requestTime), event.getRequestTime());
        assertEquals(assembler.formatTime(responseTime), event.getResponseTime());
        assertEquals(assembler.formatTime(intentRequestTime), event.getIntentRequestTime());
        assertEquals(assembler.formatTime(intentResponseTime), event.getIntentResponseTime());
        assertTrue(event.getRequestTime().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        assertEquals(1000L, event.getIntentRecognitionMillis());
        assertEquals(2000L, event.getTotalMillis());
        assertEquals("r-time", event.getMsgId());
        assertEquals("s-time", event.getSessionId());
        assertEquals("u-time", event.getUid());
        assertEquals("app-time", event.getAppId());
        assertEquals("rewritten time test", event.getRewriteQuestion());
        assertEquals("000001", event.getSlot().getMutualFund().get(0).getMatchedInfo().getCode());
        assertEquals("app", ((java.util.Map<?, ?>) event.getExtraParam()).get("channel"));
        assertEquals(Integer.valueOf(0), event.getUseOffline());
        assertEquals("workflow-time-001", event.getWorkflowId());
        assertEquals("workflow-time-001", event.getAgentResultId());
    }
}
