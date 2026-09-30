package com.example.agentgateway.application.monitoring;

import com.example.agentgateway.domain.agent.result.AgentResult;
import com.example.agentgateway.domain.model.AgentInfo;
import com.example.agentgateway.domain.model.Slot;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 异步监控边界上传递的不可变调用快照，不是数据库实体。 */
@Getter
public class InvocationMonitoringEvent {
    private final String question;
    private final String msgId;
    private final String sessionId;
    private final String uid;
    private final String appId;
    private final String rewriteQuestion;
    private final Slot slot;
    private final Object extraParam;
    private final String intentId;
    private final Map<String, Object> intentResult;
    private final String agentId;
    private final Integer useOffline;
    private final String recordId;
    private final String workflowId;
    private final String agentResultId;
    private final String requestTime;
    private final String responseTime;
    private final String intentRequestTime;
    private final String intentResponseTime;
    private final long intentRecognitionMillis;
    private final long totalMillis;
    private final Boolean streaming;
    private final String streamEndReason;
    private final Long chunkCount;
    private final Long firstChunkMillis;
    private final AgentInfo agentInfo;
    private final AgentResult answer;
    private final boolean success;
    private final String errorCode;
    private final String errorMessage;
    private final Map<String, Object> extensions;

    @Builder
    private InvocationMonitoringEvent(String question, String msgId, String sessionId, String uid,
                                      String appId, String rewriteQuestion, Slot slot,
                                      Object extraParam, String intentId,
                                      Map<String, Object> intentResult, String agentId,
                                      Integer useOffline, String recordId, String workflowId,
                                      String agentResultId, String requestTime, String responseTime,
                                      String intentRequestTime, String intentResponseTime,
                                      long intentRecognitionMillis, long totalMillis,
                                      Boolean streaming, String streamEndReason,
                                      Long chunkCount, Long firstChunkMillis,
                                      AgentInfo agentInfo, AgentResult answer, boolean success,
                                      String errorCode, String errorMessage,
                                      Map<String, Object> extensions) {
        this.question = question;
        this.msgId = msgId;
        this.sessionId = sessionId;
        this.uid = uid;
        this.appId = appId;
        this.rewriteQuestion = rewriteQuestion;
        this.slot = slot;
        this.extraParam = extraParam;
        this.intentId = intentId;
        this.intentResult = immutableMap(intentResult);
        this.agentId = agentId;
        this.useOffline = useOffline;
        this.recordId = recordId;
        this.workflowId = workflowId;
        this.agentResultId = agentResultId;
        this.requestTime = requestTime;
        this.responseTime = responseTime;
        this.intentRequestTime = intentRequestTime;
        this.intentResponseTime = intentResponseTime;
        this.intentRecognitionMillis = intentRecognitionMillis;
        this.totalMillis = totalMillis;
        this.streaming = streaming;
        this.streamEndReason = streamEndReason;
        this.chunkCount = chunkCount;
        this.firstChunkMillis = firstChunkMillis;
        this.agentInfo = agentInfo;
        this.answer = answer;
        this.success = success;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.extensions = immutableMap(extensions);
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<String, Object>(
                source == null ? Collections.<String, Object>emptyMap() : source));
    }
}
