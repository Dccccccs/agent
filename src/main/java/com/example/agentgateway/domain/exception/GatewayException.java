package com.example.agentgateway.domain.exception;

import lombok.Getter;

@Getter
public class GatewayException extends RuntimeException {
    private final String code;
    private final int httpStatus;

    public GatewayException(GatewayErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), null);
    }

    public GatewayException(GatewayErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public GatewayException(GatewayErrorCode errorCode, Throwable cause) {
        this(errorCode, errorCode.getMessage(), cause);
    }

    public GatewayException(GatewayErrorCode errorCode, String message, Throwable cause) {
        super(message == null ? errorCode.getMessage() : message, cause);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
    }
}