package com.example.agentgateway;

import com.example.agentgateway.application.filteredstream.model.RequestValidationResult;
import com.example.agentgateway.infrastructure.filteredstream.validation.ConfigurableFilteredStreamRequestValidator;
import com.example.agentgateway.infrastructure.filteredstream.validation.FilteredStreamValidationProperties;
import com.example.agentgateway.application.dto.AixzChatRequest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilteredStreamRequestValidatorTest {
    @Test
    void shouldAcceptWhenNoBlockedKeywordMatches() {
        ConfigurableFilteredStreamRequestValidator validator = validator();

        RequestValidationResult result = validator.validate(
                request("普通问题"));

        assertTrue(result.isAccepted());
    }

    @Test
    void shouldRefuseWithConfiguredReasonWhenKeywordMatches() {
        ConfigurableFilteredStreamRequestValidator validator = validator();

        RequestValidationResult result = validator.validate(
                request("包含禁止内容的问题"));

        assertFalse(result.isAccepted());
        assertEquals("拒绝回答原因", result.getRefusalMessage());
    }

    private ConfigurableFilteredStreamRequestValidator validator() {
        FilteredStreamValidationProperties properties =
                new FilteredStreamValidationProperties();
        properties.setBlockedKeywords(Arrays.asList("禁止内容", "其他规则"));
        properties.setRefusalMessage("拒绝回答原因");
        return new ConfigurableFilteredStreamRequestValidator(properties);
    }

    private AixzChatRequest request(String question) {
        AixzChatRequest request = new AixzChatRequest();
        request.setMsgId("test-msg");
        request.setQuestion(question);
        return request;
    }
}
