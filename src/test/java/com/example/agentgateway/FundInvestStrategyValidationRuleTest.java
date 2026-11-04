package com.example.agentgateway;

import com.example.agentgateway.application.textvalidation.TextFormatValidationService;
import com.example.agentgateway.application.textvalidation.model.TextFormatValidationResult;
import com.example.agentgateway.domain.filteredstream.StreamReplacementCatalog;
import com.example.agentgateway.domain.textvalidation.FundInvestStrategyValidationRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FundInvestStrategyValidationRuleTest {
    private TextFormatValidationService service;

    @BeforeEach
    void setUp() {
        service = new TextFormatValidationService(new TestCatalog(),
                Arrays.asList(new FundInvestStrategyValidationRule()));
    }

    @Test
    void shouldValidateActiveEquityContentAndReplaceSensitiveWords() {
        String input = "<summary>基金极度重视长期价值与风险平衡。</summary>\n\n"
                + "# 一、投资策略:聚焦高质量成长\n"
                + "组合重点配置具备持续盈利能力和竞争优势的企业。\n\n"
                + "# 二、基金经理操作复盘：动态优化持仓结构  \n"
                + "基金经理结合估值变化调整行业与个股配置。";

        TextFormatValidationResult result = service.validate(input,
                FundInvestStrategyValidationRule.SCENE,
                FundInvestStrategyValidationRule.ACTIVE_EQUITY_TYPE);

        assertTrue(result.getPassed());
        assertTrue(result.getProcessedText().contains("基金非常重视"));
        assertEquals(result.getProcessedText(), result.getSceneContents()
                .get(FundInvestStrategyValidationRule.CONTENT));
    }

    @Test
    void shouldCountChineseAndEnglishColonsAsTheSameHeadingType() {
        String input = "<summary>这是满足长度要求的投资策略摘要内容，用于说明基金配置方向。</summary>\n"
                + "# 一、投资策略：价值投资\n正文内容正文内容正文内容\n"
                + "# 一、投资策略:成长投资\n正文内容正文内容正文内容\n"
                + "# 二、基金经理操作复盘:动态调整\n正文内容正文内容正文内容";

        TextFormatValidationResult result = service.validate(input,
                FundInvestStrategyValidationRule.SCENE,
                FundInvestStrategyValidationRule.ACTIVE_EQUITY_TYPE);

        assertFalse(result.getPassed());
        assertTrue(result.getFailureReasons().stream()
                .anyMatch(reason -> reason.contains("实际出现2次")));
    }

    @Test
    void shouldRequireBothActiveEquityHeadingsWithNonEmptySubtitles() {
        String input = "<summary>这是满足长度要求的投资策略摘要内容，用于说明基金配置方向。</summary>\n"
                + "# 一、投资策略\n正文内容正文内容正文内容\n"
                + "# 三、其他标题：不允许\n正文内容正文内容正文内容";

        TextFormatValidationResult result = service.validate(input,
                FundInvestStrategyValidationRule.SCENE,
                FundInvestStrategyValidationRule.ACTIVE_EQUITY_TYPE);

        assertFalse(result.getPassed());
        assertTrue(result.getFailureReasons().stream()
                .anyMatch(reason -> reason.contains("# 一、投资策略")));
        assertTrue(result.getFailureReasons().stream()
                .anyMatch(reason -> reason.contains("基金经理操作复盘")));
        assertTrue(result.getFailureReasons().stream()
                .anyMatch(reason -> reason.contains("# 三、其他标题")));
    }

    @Test
    void shouldSkipHeadingRulesForOtherTypes() {
        String input = "<summary>这是其他基金类型的摘要内容，正文不要求出现固定一级标题。</summary>\n"
                + "# 任意一级标题也不参与该类型的标题校验\n"
                + "普通正文内容，用于说明投资方向、组合变化和风险控制方法。";

        TextFormatValidationResult result = service.validate(input,
                FundInvestStrategyValidationRule.SCENE, "固收类");

        assertTrue(result.getPassed());
    }

    @Test
    void shouldRequireTypeAndSummaryAsFirstNonBlankContent() {
        String input = "前置内容\n<summary></summary>\n"
                + "这里补充足够的正文内容，以确保输入文本长度超过五十个字符。";

        TextFormatValidationResult result = service.validate(input,
                FundInvestStrategyValidationRule.SCENE, " ");

        assertFalse(result.getPassed());
        assertTrue(result.getFailureReasons().stream()
                .anyMatch(reason -> reason.contains("type不能为空")));
        assertTrue(result.getFailureReasons().stream()
                .anyMatch(reason -> reason.contains("首段文本")));
        assertTrue(result.getFailureReasons().stream()
                .anyMatch(reason -> reason.contains("summary内容不能为空")));
    }

    @Test
    void shouldUseExclusiveMinimumAndInclusiveMaximumLength() {
        TextFormatValidationResult shortResult = service.validate(
                "<summary>短</summary>",
                FundInvestStrategyValidationRule.SCENE, "固收类");
        String longInput = "<summary>" + repeat("长", 1000)
                + "</summary>";
        TextFormatValidationResult longResult = service.validate(longInput,
                FundInvestStrategyValidationRule.SCENE, "固收类");

        assertFalse(shortResult.getPassed());
        assertTrue(shortResult.getFailureReasons().stream()
                .anyMatch(reason -> reason.contains("必须大于50")));
        assertFalse(longResult.getPassed());
        assertTrue(longResult.getFailureReasons().stream()
                .anyMatch(reason -> reason.contains("不超过1000")));
    }

    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }

    private static class TestCatalog implements StreamReplacementCatalog {
        @Override
        public Map<String, String> sensitiveReplacements() {
            Map<String, String> replacements =
                    new LinkedHashMap<String, String>();
            replacements.put("极度", "非常");
            return replacements;
        }
    }
}
