package com.example.agentgateway.domain.port;

import com.example.agentgateway.domain.model.AgentGatewayRequest;
import com.example.agentgateway.domain.model.IntentRecognitionResult;

public interface IntentRecognitionPort {
    IntentRecognitionResult recognize(AgentGatewayRequest request);
}
