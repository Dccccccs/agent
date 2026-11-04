package com.example.agentgateway.domain.textvalidation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 基金投资策略文本的场景校验规则。 */
public class FundInvestStrategyValidationRule
        implements SceneTextValidationRule {
    public static final String SCENE = "FUND_INVEST_STRATEGY";
    public static final String ACTIVE_EQUITY_TYPE = "主动权益类";
    public static final String CONTENT = "content";

    private static final int MIN_TEXT_LENGTH_EXCLUSIVE = 50;
    private static final int MAX_TEXT_LENGTH = 1000;
    private static final String SUMMARY_OPEN_TAG = "<summary>";
    private static final String SUMMARY_CLOSE_TAG = "</summary>";
    private static final Pattern LEVEL_ONE_HEADING = Pattern.compile(
            "(?m)^#(?!#)[ \\t]+[^\\r\\n]*\\r?$");

    private static final List<String> ACTIVE_EQUITY_HEADING_BASES =
            Collections.unmodifiableList(Arrays.asList(
                    "# 一、投资策略",
                    "# 二、基金经理操作复盘"));

    @Override
    public String scene() {
        return SCENE;
    }

    @Override
    public SceneTextValidationOutcome validate(String originalText,
                                               String processedText,
                                               String type) {
        List<String> failureReasons = new ArrayList<String>();
        Map<String, String> extractedContents =
                new LinkedHashMap<String, String>();

        validateType(type, failureReasons);
        validateOverallLength(originalText, failureReasons);
        validateSummary(processedText, failureReasons);
        if (ACTIVE_EQUITY_TYPE.equals(type)) {
            validateActiveEquityHeadings(processedText, failureReasons);
        }
        extractedContents.put(CONTENT, processedText);
        return new SceneTextValidationOutcome(extractedContents,
                failureReasons);
    }

    private void validateType(String type, List<String> failureReasons) {
        if (type == null || type.trim().isEmpty()) {
            failureReasons.add("场景“" + SCENE + "”的type不能为空");
        }
    }

    private void validateOverallLength(String originalText,
                                       List<String> failureReasons) {
        int length = originalText.length();
        if (length <= MIN_TEXT_LENGTH_EXCLUSIVE
                || length > MAX_TEXT_LENGTH) {
            failureReasons.add("场景“" + SCENE + "”的原始输入文本长度必须大于"
                    + MIN_TEXT_LENGTH_EXCLUSIVE + "且不超过"
                    + MAX_TEXT_LENGTH + "，实际为" + length);
        }
    }

    private void validateSummary(String content,
                                 List<String> failureReasons) {
        int openCount = countOccurrences(content, SUMMARY_OPEN_TAG);
        int closeCount = countOccurrences(content, SUMMARY_CLOSE_TAG);
        if (openCount != 1 || closeCount != 1) {
            failureReasons.add("<summary></summary>必须成对出现且只能出现一次，检测到开始标签"
                    + openCount + "次、结束标签" + closeCount + "次");
            return;
        }

        int summaryStart = content.indexOf(SUMMARY_OPEN_TAG);
        int summaryContentStart = summaryStart + SUMMARY_OPEN_TAG.length();
        int summaryEnd = content.indexOf(SUMMARY_CLOSE_TAG);
        if (summaryEnd < summaryContentStart) {
            failureReasons.add("<summary></summary>开始、结束顺序不正确");
            return;
        }
        if (firstNonWhitespaceIndex(content) != summaryStart) {
            failureReasons.add("首段文本必须使用<summary></summary>包裹");
        }
        if (content.substring(summaryContentStart, summaryEnd)
                .trim().isEmpty()) {
            failureReasons.add("summary内容不能为空");
        }

        Matcher firstHeading = LEVEL_ONE_HEADING.matcher(content);
        if (firstHeading.find()
                && summaryEnd + SUMMARY_CLOSE_TAG.length()
                > firstHeading.start()) {
            failureReasons.add("summary必须完整出现在首个Markdown一级标题之前");
        }
    }

    private void validateActiveEquityHeadings(
            String content, List<String> failureReasons) {
        Matcher matcher = LEVEL_ONE_HEADING.matcher(content);
        while (matcher.find()) {
            String heading = normalizeHeading(matcher.group());
            if (!isAllowedActiveEquityHeading(heading)) {
                failureReasons.add("type=“" + ACTIVE_EQUITY_TYPE
                        + "”时存在不允许的Markdown一级标题“"
                        + heading + "”");
            }
        }

        for (String headingBase : ACTIVE_EQUITY_HEADING_BASES) {
            int count = countHeadingByBase(content, headingBase);
            if (count != 1) {
                failureReasons.add("type=“" + ACTIVE_EQUITY_TYPE
                        + "”时必须且只能出现一次一级标题“"
                        + headingBase + "：{副标题}”或“"
                        + headingBase + ":{副标题}”，实际出现"
                        + count + "次");
            }
        }
    }

    private int countHeadingByBase(String content, String headingBase) {
        Matcher matcher = LEVEL_ONE_HEADING.matcher(content);
        int count = 0;
        while (matcher.find()) {
            String heading = normalizeHeading(matcher.group());
            if (isHeadingWithNonEmptySubtitle(heading, headingBase)) {
                count++;
            }
        }
        return count;
    }

    private boolean isAllowedActiveEquityHeading(String heading) {
        for (String headingBase : ACTIVE_EQUITY_HEADING_BASES) {
            if (isHeadingWithNonEmptySubtitle(heading, headingBase)) {
                return true;
            }
        }
        return false;
    }

    private boolean isHeadingWithNonEmptySubtitle(String heading,
                                                   String headingBase) {
        if (!heading.startsWith(headingBase)) {
            return false;
        }
        int separatorIndex = headingBase.length();
        if (heading.length() <= separatorIndex) {
            return false;
        }
        char separator = heading.charAt(separatorIndex);
        if (separator != '：' && separator != ':') {
            return false;
        }
        String subtitle = heading.substring(separatorIndex + 1);
        return !subtitle.isEmpty() && subtitle.equals(subtitle.trim());
    }

    private int countOccurrences(String text, String target) {
        int count = 0;
        int fromIndex = 0;
        while (fromIndex <= text.length() - target.length()) {
            int index = text.indexOf(target, fromIndex);
            if (index < 0) {
                break;
            }
            count++;
            fromIndex = index + target.length();
        }
        return count;
    }

    private int firstNonWhitespaceIndex(String content) {
        for (int index = 0; index < content.length(); index++) {
            if (!Character.isWhitespace(content.charAt(index))) {
                return index;
            }
        }
        return content.length();
    }

    private String normalizeHeading(String value) {
        int end = value.length();
        if (end > 0 && value.charAt(end - 1) == '\r') {
            end--;
        }
        while (end > 0) {
            char character = value.charAt(end - 1);
            if (character != ' ' && character != '\t') {
                break;
            }
            end--;
        }
        return value.substring(0, end);
    }
}
