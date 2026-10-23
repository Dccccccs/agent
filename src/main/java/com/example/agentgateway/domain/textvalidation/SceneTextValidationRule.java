package com.example.agentgateway.domain.textvalidation;

/** 某个文本场景专属的结构校验规则。 */
public interface SceneTextValidationRule {
    /** 外部调用方传入的场景值。 */
    String scene();

    /**
     * @param originalText 原始文本，用于原始篇幅校验
     * @param processedText 完成敏感词替换后的文本，用于结构校验和内容提取
     */
    SceneTextValidationOutcome validate(String originalText,
                                        String processedText);
}
