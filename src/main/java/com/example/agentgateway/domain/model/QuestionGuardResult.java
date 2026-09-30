package com.example.agentgateway.domain.model;

import lombok.Builder;
import lombok.Getter;

/** 问题安全检查结果。 */
@Getter
@Builder
public class QuestionGuardResult {
    private final boolean passed;
    private final String reason;
}
