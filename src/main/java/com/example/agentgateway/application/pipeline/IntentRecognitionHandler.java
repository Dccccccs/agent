package com.example.agentgateway.application.pipeline;

import com.example.agentgateway.domain.exception.GatewayErrorCode;
import com.example.agentgateway.domain.exception.GatewayException;
import com.example.agentgateway.domain.model.GatewayContext;
import com.example.agentgateway.domain.model.IntentRecognitionResult;
import com.example.agentgateway.domain.port.IntentRecognitionPort;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Order(20)
public class IntentRecognitionHandler implements GatewayHandler {
    private final IntentRecognitionPort recognitionPort;

    public IntentRecognitionHandler(IntentRecognitionPort recognitionPort) {
        this.recognitionPort = recognitionPort;
    }

    public void handle(GatewayContext context) {
        long startedAt = System.currentTimeMillis();
        context.beginIntentRecognition(Instant.ofEpochMilli(startedAt));
        try {
            IntentRecognitionResult result = recognitionPort.recognize(context.getRequest());
            long completedAt = System.currentTimeMillis();
            context.completeIntentRecognition(result, Instant.ofEpochMilli(completedAt),
                    elapsedMillis(startedAt, completedAt));
        } catch (GatewayException ex) {
            recordFailedRecognition(context, startedAt);
            throw ex;
        } catch (RuntimeException ex) {
            recordFailedRecognition(context, startedAt);
            throw new GatewayException(GatewayErrorCode.INTENT_RECOGNITION_FAILED, ex);
        }
    }

    private void recordFailedRecognition(GatewayContext context, long startedAt) {
        long completedAt = System.currentTimeMillis();
        context.failIntentRecognition(Instant.ofEpochMilli(completedAt),
                elapsedMillis(startedAt, completedAt));
    }

    private long elapsedMillis(long startedAt, long completedAt) {
        return Math.max(completedAt - startedAt, 0L);
    }
}
