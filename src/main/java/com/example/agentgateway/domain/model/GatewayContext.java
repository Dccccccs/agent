package com.example.agentgateway.domain.model;

import com.example.agentgateway.domain.agent.result.AgentResult;
import com.example.agentgateway.domain.agent.result.OfflineAgentResult;
import com.example.agentgateway.domain.agent.result.OnlineAgentResult;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;

import java.time.Instant;
import java.util.Map;

@Getter
@Builder
public class GatewayContext {
    @NonNull
    private final AgentGatewayRequest request;
    private String intentId;
    @Singular(value = "intentEntry", ignoreNullCollections = true)
    private Map<String, Object> intentResult;
    private String agentId;
    private AgentResult result;
    private AgentInfo agentInfo;
    private String agentResultId;
    private Instant intentRequestTime;
    private Instant intentResponseTime;
    private long intentRecognitionMillis;

    public void beginIntentRecognition(Instant requestTime) {
        this.intentRequestTime = requestTime == null
                ? Instant.ofEpochMilli(System.currentTimeMillis()) : requestTime;
    }

    public void completeIntentRecognition(IntentRecognitionResult recognitionResult,
                                          Instant responseTime, long recognitionMillis) {
        if (recognitionResult == null) {
            throw new IllegalArgumentException("意图识别结果不能为空");
        }
        this.intentId = recognitionResult.getIntentId();
        this.intentResult = recognitionResult.getIntentResult();
        finishIntentRecognition(responseTime, recognitionMillis);
    }

    public void failIntentRecognition(Instant responseTime, long recognitionMillis) {
        finishIntentRecognition(responseTime, recognitionMillis);
    }

    private void finishIntentRecognition(Instant responseTime, long recognitionMillis) {
        this.intentResponseTime = responseTime == null
                ? Instant.ofEpochMilli(System.currentTimeMillis()) : responseTime;
        this.intentRecognitionMillis = Math.max(recognitionMillis, 0L);
    }

    public void routeToAgent(String agentId) {
        if (isBlank(agentId)) {
            throw new IllegalArgumentException("agentId不能为空");
        }
        this.agentId = agentId;
    }

    public void completeAgentExecution(AgentInfo agentInfo, AgentResult result) {
        if (agentInfo == null || result == null) {
            throw new IllegalArgumentException("Agent信息和结果不能为空");
        }
        if (!Integer.valueOf(0).equals(agentInfo.getUseOffline())
                && !Integer.valueOf(1).equals(agentInfo.getUseOffline())) {
            throw new IllegalArgumentException("Agent的useOffline只能为0或1");
        }

        validateResultType(agentInfo, result);
        String selectedResultId = agentInfo.usesOfflineResult()
                ? ((OfflineAgentResult) result).getRecordId()
                : ((OnlineAgentResult) result).getWorkflowId();
        if (isBlank(selectedResultId)) {
            throw new IllegalArgumentException(agentInfo.usesOfflineResult()
                    ? "离线Agent结果的recordId不能为空"
                    : "在线Agent结果的workflowId不能为空");
        }

        this.agentInfo = agentInfo;
        this.result = result;
        this.agentResultId = selectedResultId;
    }

    private void validateResultType(AgentInfo agentInfo, AgentResult result) {
        if (agentInfo.usesOfflineResult() && !(result instanceof OfflineAgentResult)) {
            throw new IllegalArgumentException("离线Agent必须返回OfflineAgentResult");
        }
        if (!agentInfo.usesOfflineResult() && !(result instanceof OnlineAgentResult)) {
            throw new IllegalArgumentException("在线Agent必须返回OnlineAgentResult");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
