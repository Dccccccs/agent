package com.example.agentgateway.application.filteredstream.model;

import lombok.Getter;

@Getter
public class RequestValidationResult {
    private final boolean accepted;
    private final String refusalMessage;

    private RequestValidationResult(boolean accepted, String refusalMessage) {
        this.accepted = accepted;
        this.refusalMessage = refusalMessage;
    }

    public static RequestValidationResult accepted() {
        return new RequestValidationResult(true, null);
    }

    public static RequestValidationResult refused(String refusalMessage) {
        if (refusalMessage == null || refusalMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("拒绝回答原因不能为空");
        }
        return new RequestValidationResult(false, refusalMessage);
    }
}
