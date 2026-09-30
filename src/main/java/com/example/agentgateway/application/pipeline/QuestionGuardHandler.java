package com.example.agentgateway.application.pipeline;

import com.example.agentgateway.domain.exception.GatewayErrorCode;
import com.example.agentgateway.domain.exception.GatewayException;
import com.example.agentgateway.domain.model.GatewayContext;
import com.example.agentgateway.domain.model.QuestionGuardResult;
import com.example.agentgateway.domain.port.QuestionGuardPort;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class QuestionGuardHandler implements GatewayHandler {
    private final QuestionGuardPort guardPort;

    public QuestionGuardHandler(QuestionGuardPort guardPort) {
        this.guardPort = guardPort;
    }

    public void handle(GatewayContext context) {
        final QuestionGuardResult result;
        try {
            result = guardPort.check(
                    context.getRequest().getUid(), context.getRequest().getQuestion());
        } catch (GatewayException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new GatewayException(GatewayErrorCode.QUESTION_GUARD_FAILED, ex);
        }
        if (!result.isPassed()) {
            throw new GatewayException(GatewayErrorCode.QUESTION_REJECTED, result.getReason());
        }
    }
}
