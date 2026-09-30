package com.example.agentgateway.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;

import java.util.Map;

@Getter
@Builder
public class IntentRecognitionResult {
    @NonNull
    private final String intentId;
    @Singular(value = "intentResultEntry", ignoreNullCollections = true)
    private final Map<String, Object> intentResult;
}
