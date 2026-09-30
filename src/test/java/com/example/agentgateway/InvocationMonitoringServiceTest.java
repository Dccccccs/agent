package com.example.agentgateway;

import com.example.agentgateway.application.InvocationMonitoringService;
import com.example.agentgateway.application.monitoring.InvocationMonitoringEvent;
import com.example.agentgateway.application.port.InvocationMonitoringSink;
import com.example.agentgateway.domain.agent.result.WeatherAgentResult;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InvocationMonitoringServiceTest {
    @Test
    void shouldPublishRecordToAllSinks() {
        InvocationMonitoringSink sink = mock(InvocationMonitoringSink.class);
        InvocationMonitoringService service = new InvocationMonitoringService(Collections.singletonList(sink));
        InvocationMonitoringEvent event = InvocationMonitoringEvent.builder()
                .msgId("r1")
                .uid("u1")
                .question("q")
                .intentId("WEATHER_INTENT")
                .intentResult(Collections.<String, Object>singletonMap("confidence", 0.95D))
                .agentId("weather-agent")
                .useOffline(0)
                .intentRecognitionMillis(12L)
                .totalMillis(30L)
                .workflowId("weather-workflow-r1")
                .agentResultId("weather-workflow-r1")
                .answer(WeatherAgentResult.builder()
                        .workflowId("weather-workflow-r1")
                        .answer("answer")
                        .build())
                .success(true)
                .extensions(new HashMap<String, Object>())
                .build();
        service.publish(event);
        verify(sink).save(event);
    }
}
