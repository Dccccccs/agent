package com.example.agentgateway;

import com.example.agentgateway.application.textvalidation.TextFormatValidationService;
import com.example.agentgateway.application.textvalidation.model.TextFormatValidationResult;
import com.example.agentgateway.domain.filteredstream.StreamReplacementCatalog;
import com.example.agentgateway.domain.textvalidation.FundChangeAnalysisValidationRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextFormatValidationServiceTest {
    private TextFormatValidationService service;

    @BeforeEach
    void setUp() {
        service = new TextFormatValidationService(new TestCatalog(),
                Arrays.asList(new FundChangeAnalysisValidationRule()));
    }

    @Test
    void shouldReplaceSensitiveWordsValidateAndExtractAllSections() {
        String heavy = section("重仓股变化", "当前达到极端水平",
                "一、重仓股变化", "二、基金经理操作解析");
        String industry = section("基金行业变化", "行业极度集中",
                "一、基金持仓变化", "二、基金经理操作解析");
        String position = section("仓位变化", "仓位保持稳定",
                "一、基金持仓变化", "二、基金经理操作解析");
        String input = "标签外说明\n" + position + industry + heavy + "\n结束说明";

        TextFormatValidationResult result = service.validate(input,
                FundChangeAnalysisValidationRule.SCENE);

        assertTrue(result.getPassed());
        assertTrue(result.getProcessedText().contains("超出常规水平"));
        assertTrue(result.getProcessedText().contains("行业非常集中"));
        assertTrue(result.getSceneContents()
                .get(FundChangeAnalysisValidationRule.HEAVY_STOCK_CHANGES)
                .contains("超出常规水平"));
        assertTrue(result.getSceneContents()
                .get(FundChangeAnalysisValidationRule.FUND_INDUSTRY_CHANGES)
                .contains("行业非常集中"));
        assertTrue(result.getSceneContents()
                .get(FundChangeAnalysisValidationRule.POSITION_CHANGES)
                .contains("仓位保持稳定"));
        assertEquals(3, result.getSceneContents().size());
        assertTrue(result.getFailureReasons().isEmpty());
    }

    @Test
    void shouldUseOriginalLengthsBeforeSensitiveReplacement() {
        String heavy = section("重仓股变化", repeat("长", 1350),
                "一、重仓股变化", "二、基金经理操作解析");
        String input = heavy
                + section("基金行业变化", "行业情况", "一、基金持仓变化",
                "二、基金经理操作解析")
                + section("仓位变化", "仓位情况", "一、基金持仓变化",
                "二、基金经理操作解析");

        TextFormatValidationResult result = service.validate(input,
                FundChangeAnalysisValidationRule.SCENE);

        assertTrue(result.getPassed());
        assertTrue(result.getSceneContents()
                        .get(FundChangeAnalysisValidationRule.HEAVY_STOCK_CHANGES)
                        .length() > 1500,
                "敏感词替换后的分段可以超过1500，长度应以原文为准");
    }

    @Test
    void shouldReplaceWholeTextBeforeValidatingHeadings() {
        String input = section("重仓股变化", "重仓情况", "旧标题",
                "二、基金经理操作解析")
                + section("基金行业变化", "行业情况", "一、基金持仓变化",
                "二、基金经理操作解析")
                + section("仓位变化", "仓位情况", "一、基金持仓变化",
                "二、基金经理操作解析");

        TextFormatValidationResult result = service.validate(input,
                FundChangeAnalysisValidationRule.SCENE);

        assertTrue(result.getPassed());
        assertTrue(result.getSceneContents()
                .get(FundChangeAnalysisValidationRule.HEAVY_STOCK_CHANGES)
                .contains("# 一、重仓股变化"));
    }

    @Test
    void shouldAllowSubtitlesSeparatedByFullWidthColon() {
        String input = section("重仓股变化", "重仓情况",
                "一、重仓股变化：核心标的调整", "二、基金经理操作解析：未来展望")
                + section("基金行业变化", "行业情况",
                "一、基金持仓变化：配置进一步集中", "二、基金经理操作解析：行业判断")
                + section("仓位变化", "仓位情况",
                "一、基金持仓变化：仓位保持稳定", "二、基金经理操作解析：后续策略");

        TextFormatValidationResult result = service.validate(input,
                FundChangeAnalysisValidationRule.SCENE);

        assertTrue(result.getPassed());
    }

    @Test
    void shouldRejectUnsupportedSubtitleSeparators() {
        String input = section("重仓股变化", "重仓情况",
                "一、重仓股变化——核心标的调整", "二、基金经理操作解析")
                + section("基金行业变化", "行业情况",
                "一、基金持仓变化", "二、基金经理操作解析")
                + section("仓位变化", "仓位情况",
                "一、基金持仓变化", "二、基金经理操作解析");

        TextFormatValidationResult result = service.validate(input,
                FundChangeAnalysisValidationRule.SCENE);

        assertFalse(result.getPassed());
        assertTrue(result.getFailureReasons().stream()
                .anyMatch(reason -> reason.contains("——核心标的调整")));
    }

    @Test
    void shouldReportDuplicateTagsAndAllRecognizableFailures() {
        String validIndustry = section("基金行业变化", "行业情况",
                "一、基金持仓变化", "二、基金经理操作解析");
        String invalidPosition = "<仓位变化>普通正文\n"
                + "# 一、其他标题\n正文不足</仓位变化>";
        String input = section("重仓股变化", "重仓情况",
                "一、重仓股变化", "二、基金经理操作解析")
                + section("重仓股变化", "重复内容",
                "一、重仓股变化", "二、基金经理操作解析")
                + validIndustry + invalidPosition;

        TextFormatValidationResult result = service.validate(input,
                FundChangeAnalysisValidationRule.SCENE);

        assertFalse(result.getPassed());
        assertTrue(result.getSceneContents().isEmpty());
        assertTrue(result.getFailureReasons().stream()
                .anyMatch(reason -> reason.contains("开始标签2次")));
        assertTrue(result.getFailureReasons().stream()
                .anyMatch(reason -> reason.contains("原始内容长度")));
        assertTrue(result.getFailureReasons().stream()
                .anyMatch(reason -> reason.contains("<summary></summary>")));
        assertTrue(result.getFailureReasons().stream()
                .anyMatch(reason -> reason.contains("不允许的Markdown一级标题")));
    }

    @Test
    void shouldRequireEachExactLevelOneTitleOnceButAllowLowerLevels() {
        String heavy = "<重仓股变化>\n<summary>摘要内容</summary>\n"
                + "# 一、重仓股变化\n## 补充标题\n正文正文正文正文正文正文\n"
                + "# 一、重仓股变化\n# 三、非法标题\n"
                + "</重仓股变化>";
        String input = heavy
                + section("基金行业变化", "行业情况", "一、基金持仓变化",
                "二、基金经理操作解析")
                + section("仓位变化", "仓位情况", "一、基金持仓变化",
                "二、基金经理操作解析");

        TextFormatValidationResult result = service.validate(input,
                FundChangeAnalysisValidationRule.SCENE);

        assertFalse(result.getPassed());
        assertTrue(result.getFailureReasons().stream()
                .anyMatch(reason -> reason.contains("实际出现2次")));
        assertTrue(result.getFailureReasons().stream()
                .anyMatch(reason -> reason.contains("二、基金经理操作解析")
                        && reason.contains("实际出现0次")));
        assertTrue(result.getFailureReasons().stream()
                .anyMatch(reason -> reason.contains("# 三、非法标题")));
        assertFalse(result.getFailureReasons().stream()
                .anyMatch(reason -> reason.contains("## 补充标题")));
    }

    @Test
    void shouldRequireNonEmptySummaryAsFirstParagraphBeforeHeading() {
        String heavy = "<重仓股变化>\n前置正文\n<summary></summary>\n"
                + "# 一、重仓股变化\n正文正文正文正文正文正文正文正文\n"
                + "# 二、基金经理操作解析\n正文正文正文正文正文正文正文正文\n"
                + "</重仓股变化>";
        String input = heavy
                + section("基金行业变化", "行业情况", "一、基金持仓变化",
                "二、基金经理操作解析")
                + section("仓位变化", "仓位情况", "一、基金持仓变化",
                "二、基金经理操作解析");

        TextFormatValidationResult result = service.validate(input,
                FundChangeAnalysisValidationRule.SCENE);

        assertFalse(result.getPassed());
        assertTrue(result.getFailureReasons().stream()
                .anyMatch(reason -> reason.contains("首段文本")));
        assertTrue(result.getFailureReasons().stream()
                .anyMatch(reason -> reason.contains("summary内容不能为空")));
    }

    @Test
    void shouldRejectUnsupportedSceneAndOverlongOriginalText() {
        String input = repeat("文本", 1600);

        TextFormatValidationResult result = service.validate(input, "UNKNOWN");

        assertFalse(result.getPassed());
        assertTrue(result.getProcessedText().length() > 3000);
        assertEquals(2, result.getFailureReasons().size());
        assertTrue(result.getFailureReasons().get(0).contains("实际为3200"));
        assertTrue(result.getFailureReasons().get(1).contains("UNKNOWN"));
    }

    private String section(String tagName,
                           String summary,
                           String firstTitle,
                           String secondTitle) {
        return "<" + tagName + ">\n"
                + "<summary>" + summary + "</summary>\n\n"
                + "# " + firstTitle + "\n"
                + "这是第一部分正文，用于说明本部分的主要变化和相关分析结果。\n\n"
                + "# " + secondTitle + "\n"
                + "这是第二部分正文，用于说明基金经理的操作思路和配置方向。\n"
                + "</" + tagName + ">\n";
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
            replacements.put("极端水平", "超出常规水平");
            replacements.put("极端", "偏高");
            replacements.put("极度", "非常");
            replacements.put("旧标题", "一、重仓股变化");
            replacements.put("长", "这是明显变长的替换内容");
            return replacements;
        }
    }
}
