package com.example.agentgateway.domain.exception;

import lombok.Getter;

@Getter
public enum GatewayErrorCode {
    INVALID_PARAMETER("INVALID_PARAMETER", "请求参数不合法", 400),
    QUESTION_REJECTED("QUESTION_REJECTED", "问题未通过安全检查", 400),
    QUESTION_GUARD_FAILED("QUESTION_GUARD_FAILED", "问题拦截服务调用失败", 502),
    INTENT_RECOGNITION_FAILED("INTENT_RECOGNITION_FAILED", "意图识别服务调用失败", 502),
    AGENT_MAPPING_NOT_FOUND("AGENT_MAPPING_NOT_FOUND", "意图未配置Agent映射", 422),
    AGENT_ROUTE_FAILED("AGENT_ROUTE_FAILED", "Agent路由查询失败", 502),
    AGENT_NOT_FOUND("AGENT_NOT_FOUND", "未找到可执行的Agent", 422),
    AGENT_EXECUTION_FAILED("AGENT_EXECUTION_FAILED", "Agent执行失败", 502),
    AGENT_RESULT_CONVERTER_NOT_FOUND("AGENT_RESULT_CONVERTER_NOT_FOUND", "未配置Agent结果转换器", 500),
    INVALID_AGENT_RESULT("INVALID_AGENT_RESULT", "Agent返回结果不合法", 502),
    AGENT_RESULT_CONVERT_FAILED("AGENT_RESULT_CONVERT_FAILED", "Agent结果转换失败", 500),
    GATEWAY_EXECUTION_FAILED("GATEWAY_EXECUTION_FAILED", "Agent网关处理失败", 500),
    SYSTEM_ERROR("SYSTEM_ERROR", "系统内部异常", 500);

    private final String code;
    private final String message;
    private final int httpStatus;

    GatewayErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}