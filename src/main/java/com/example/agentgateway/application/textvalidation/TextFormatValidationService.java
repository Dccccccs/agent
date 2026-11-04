package com.example.agentgateway.application.textvalidation;

import com.example.agentgateway.application.textvalidation.model.TextFormatValidationResult;
import com.example.agentgateway.domain.filteredstream.StreamReplacementCatalog;
import com.example.agentgateway.domain.textvalidation.SceneTextValidationOutcome;
import com.example.agentgateway.domain.textvalidation.SceneTextValidationRule;
import com.example.agentgateway.domain.textvalidation.SensitiveTextReplacer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 文本敏感词替换、通用校验及场景校验的统一入口。 */
@Service
public class TextFormatValidationService {
    private final SensitiveTextReplacer sensitiveTextReplacer;
    private final Map<String, SceneTextValidationRule> sceneRules;

    public TextFormatValidationService(
            StreamReplacementCatalog replacementCatalog,
            List<SceneTextValidationRule> rules) {
        this.sensitiveTextReplacer = new SensitiveTextReplacer(
                replacementCatalog);
        this.sceneRules = indexRules(rules);
    }

    /**
     * 先对全文执行敏感词替换，再按指定场景规则校验。
     * 全文及分段长度范围均由场景规则决定。
     */
    public TextFormatValidationResult validate(String text, String scene) {
        return validate(text, scene, null);
    }

    /**
     * 先对全文执行敏感词替换，再按指定场景和内容类型执行校验。
     */
    public TextFormatValidationResult validate(String text,
                                               String scene,
                                               String type) {
        if (text == null) {
            return TextFormatValidationResult.failed(null,
                    Collections.singletonList("输入文本不能为空"));
        }

        String processedText = sensitiveTextReplacer.replace(text);
        List<String> failureReasons = new ArrayList<String>();
        SceneTextValidationRule rule = sceneRules.get(scene);
        if (rule == null) {
            failureReasons.add("不支持的文本校验场景：" + String.valueOf(scene));
            return TextFormatValidationResult.failed(processedText,
                    failureReasons);
        }

        SceneTextValidationOutcome outcome = rule.validate(
                text, processedText, type);
        failureReasons.addAll(outcome.getFailureReasons());
        if (!failureReasons.isEmpty()) {
            return TextFormatValidationResult.failed(processedText,
                    failureReasons);
        }
        return TextFormatValidationResult.passed(processedText,
                outcome.getExtractedContents());
    }

    private Map<String, SceneTextValidationRule> indexRules(
            List<SceneTextValidationRule> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException("至少需要注册一个文本场景校验规则");
        }
        Map<String, SceneTextValidationRule> result =
                new LinkedHashMap<String, SceneTextValidationRule>();
        for (SceneTextValidationRule rule : rules) {
            if (rule == null || rule.scene() == null
                    || rule.scene().trim().isEmpty()) {
                throw new IllegalArgumentException("文本场景校验规则及场景值不能为空");
            }
            if (result.put(rule.scene(), rule) != null) {
                throw new IllegalArgumentException(
                        "文本校验场景重复注册：" + rule.scene());
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
