package com.example.agentgateway.application.textvalidation.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 通用文本格式校验方法的返回结果。 */
@Getter
public class TextFormatValidationResult {
    private final Boolean passed;
    private final String processedText;
    private final Map<String, String> sceneContents;
    private final List<String> failureReasons;

    private TextFormatValidationResult(Boolean passed,
                                       String processedText,
                                       Map<String, String> sceneContents,
                                       List<String> failureReasons) {
        this.passed = passed;
        this.processedText = processedText;
        this.sceneContents = Collections.unmodifiableMap(
                new LinkedHashMap<String, String>(sceneContents));
        this.failureReasons = Collections.unmodifiableList(
                new ArrayList<String>(failureReasons));
    }

    public static TextFormatValidationResult passed(
            String processedText,
            Map<String, String> sceneContents) {
        return new TextFormatValidationResult(Boolean.TRUE, processedText,
                sceneContents,
                Collections.<String>emptyList());
    }

    public static TextFormatValidationResult failed(
            String processedText, List<String> failureReasons) {
        if (failureReasons == null || failureReasons.isEmpty()) {
            throw new IllegalArgumentException("校验失败原因不能为空");
        }
        return new TextFormatValidationResult(Boolean.FALSE, processedText,
                Collections.<String, String>emptyMap(),
                failureReasons);
    }
}
