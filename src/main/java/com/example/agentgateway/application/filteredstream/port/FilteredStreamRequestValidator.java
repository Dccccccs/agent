package com.example.agentgateway.application.filteredstream.port;

import com.example.agentgateway.application.filteredstream.model.RequestValidationResult;
import com.example.agentgateway.application.dto.AixzChatRequest;

/**
 * 流式请求的业务校验扩展点。基础的必填字段由 Bean Validation 处理；内容
 * 审核、权限或业务开关等“拒绝回答”规则通过本端口扩展。
 */
public interface FilteredStreamRequestValidator {
    /** 校验发生在建立 SSE 和调用外部模型之前。 */
    RequestValidationResult validate(AixzChatRequest request);
}
