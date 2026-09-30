package com.example.agentgateway.domain.port;

import com.example.agentgateway.domain.model.QuestionGuardResult;

public interface QuestionGuardPort {
    QuestionGuardResult check(String uid, String question);
}
