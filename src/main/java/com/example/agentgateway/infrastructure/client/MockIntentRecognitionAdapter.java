package com.example.agentgateway.infrastructure.client;

import com.example.agentgateway.domain.model.AgentGatewayRequest;
import com.example.agentgateway.domain.model.IntentRecognitionResult;
import com.example.agentgateway.domain.port.IntentRecognitionPort;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MockIntentRecognitionAdapter implements IntentRecognitionPort {
    public IntentRecognitionResult recognize(AgentGatewayRequest request) {
        String question = request.getQuestion();
        if (question.contains("模拟意图异常")) {
            throw new IllegalStateException("模拟下游意图识别服务不可用");
        }
        String intentId = "UNKNOWN_INTENT";
        String matchedKeyword = "";
        if (question.contains("流式") || question.toLowerCase().contains("stream")) {
            intentId = "STREAM_CHAT_INTENT";
            matchedKeyword = "流式/stream";
        } else if (question.contains("天气") || question.toLowerCase().contains("weather")) {
            intentId = "WEATHER_INTENT";
            matchedKeyword = "天气";
        } else if (question.contains("订单") || question.contains("物流")) {
            intentId = "ORDER_QUERY_INTENT";
            matchedKeyword = "订单/物流";
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("confidence", "UNKNOWN_INTENT".equals(intentId) ? 0.30D : 0.95D);
        result.put("matchedKeyword", matchedKeyword);
        return IntentRecognitionResult.builder()
                .intentId(intentId)
                .intentResult(result)
                .build();
    }
}
