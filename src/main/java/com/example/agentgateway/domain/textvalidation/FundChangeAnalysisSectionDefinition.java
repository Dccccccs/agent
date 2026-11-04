package com.example.agentgateway.domain.textvalidation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 基金变化分析场景中单个标签分段的结构定义。 */
final class FundChangeAnalysisSectionDefinition {
    private final String tagName;
    private final String fieldName;
    private final List<String> allowedTitles;

    FundChangeAnalysisSectionDefinition(String tagName,
                                        String fieldName,
                                        List<String> allowedTitles) {
        this.tagName = tagName;
        this.fieldName = fieldName;
        this.allowedTitles = Collections.unmodifiableList(
                new ArrayList<String>(allowedTitles));
    }

    String getTagName() {
        return tagName;
    }

    String getFieldName() {
        return fieldName;
    }

    List<String> getAllowedTitles() {
        return allowedTitles;
    }
}
