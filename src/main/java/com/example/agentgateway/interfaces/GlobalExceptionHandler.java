package com.example.agentgateway.interfaces;

import com.example.agentgateway.domain.exception.GatewayErrorCode;
import com.example.agentgateway.domain.exception.GatewayException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<Map<String, Object>> handleGatewayException(GatewayException ex) {
        if (ex.getHttpStatus() >= 500) {
            log.error("Agent网关调用失败, code={}", ex.getCode(), ex);
        }
        return ResponseEntity.status(HttpStatus.valueOf(ex.getHttpStatus()))
                .body(error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().isEmpty()
                ? GatewayErrorCode.INVALID_PARAMETER.getMessage()
                : ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(error(GatewayErrorCode.INVALID_PARAMETER.getCode(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpectedException(Exception ex) {
        log.error("未处理的Agent网关异常", ex);
        GatewayErrorCode errorCode = GatewayErrorCode.SYSTEM_ERROR;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(error(errorCode.getCode(), errorCode.getMessage()));
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("success", false);
        body.put("code", code);
        body.put("message", message);
        return body;
    }
}
