package com.example.agentgateway.domain.textvalidation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 场景规则的内部校验结果。 */
public class SceneTextValidationOutcome {
    private final Map<String, String> extractedContents;
    private final List<String> failureReasons;

    public SceneTextValidationOutcome(Map<String, String> extractedContents,
                                      List<String> failureReasons) {
        Map<String, String> contents = extractedContents == null
                ? Collections.<String, String>emptyMap() : extractedContents;
        List<String> reasons = failureReasons == null
                ? Collections.<String>emptyList() : failureReasons;
        this.extractedContents = Collections.unmodifiableMap(
                new LinkedHashMap<String, String>(contents));
        this.failureReasons = Collections.unmodifiableList(
                new ArrayList<String>(reasons));
    }

    public String getExtractedContent(String fieldName) {
        return extractedContents.get(fieldName);
    }

    public Map<String, String> getExtractedContents() {
        return extractedContents;
    }

    public List<String> getFailureReasons() {
        return failureReasons;
    }
}
