package com.example.agentgateway.infrastructure.filteredstream.validation;

import com.example.agentgateway.application.filteredstream.model.RequestValidationResult;
import com.example.agentgateway.application.filteredstream.port.FilteredStreamRequestValidator;
import com.example.agentgateway.application.dto.AixzChatRequest;

/**
 * 默认的配置化请求校验适配器。命中 blocked-keywords 时返回业务拒绝，
 * 不抛异常；应用服务会把拒绝结果转换成 SSE 兜底帧和 DONE。
 * 生产环境提供自定义 Validator Bean 后，该默认实现会自动退出。
 */
public class ConfigurableFilteredStreamRequestValidator
        implements FilteredStreamRequestValidator {
    private final FilteredStreamValidationProperties properties;

    public ConfigurableFilteredStreamRequestValidator(
            FilteredStreamValidationProperties properties) {
        this.properties = properties;
    }

    @Override
    public RequestValidationResult validate(AixzChatRequest request) {
        String question = request == null ? null : request.getQuestion();
        if (question == null) {
            return RequestValidationResult.refused(properties.getRefusalMessage());
        }
        for (String keyword : properties.getBlockedKeywords()) {
            // 空配置项直接忽略，避免配置中心误下发空字符串后拒绝所有问题。
            if (keyword != null && !keyword.isEmpty()
                    && question.contains(keyword)) {
                return RequestValidationResult.refused(
                        properties.getRefusalMessage());
            }
        }
        return RequestValidationResult.accepted();
    }
}
