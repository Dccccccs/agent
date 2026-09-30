package com.example.agentgateway.infrastructure.client;

import com.example.agentgateway.domain.model.QuestionGuardResult;
import com.example.agentgateway.domain.port.QuestionGuardPort;
import org.springframework.stereotype.Component;

@Component
public class MockQuestionGuardAdapter implements QuestionGuardPort {
    public QuestionGuardResult check(String uid, String question) {
        if (question.contains("违禁") || question.contains("攻击")) {
            return QuestionGuardResult.builder()
                    .passed(false)
                    .reason("问题未通过安全检查")
                    .build();
        }
        return QuestionGuardResult.builder()
                .passed(true)
                .reason("PASS")
                .build();
    }
}
