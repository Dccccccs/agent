package com.example.agentgateway.domain.textvalidation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 基金重仓股、行业与仓位变化文本的场景规则。 */
public class FundChangeAnalysisValidationRule
        implements SceneTextValidationRule {
    public static final String SCENE = "FUND_CHANGE_ANALYSIS";
    public static final String HEAVY_STOCK_CHANGES = "heavyStockChanges";
    public static final String FUND_INDUSTRY_CHANGES = "fundIndustryChanges";
    public static final String POSITION_CHANGES = "positionChanges";

    private static final int MIN_SECTION_LENGTH = 50;
    private static final int MAX_SECTION_LENGTH = 1500;
    private static final String SUMMARY_OPEN_TAG = "<summary>";
    private static final String SUMMARY_CLOSE_TAG = "</summary>";
    private static final Pattern LEVEL_ONE_HEADING = Pattern.compile(
            "(?m)^#(?!#)[ \\t]+[^\\r\\n]*\\r?$");

    @Override
    public String scene() {
        return SCENE;
    }

    @Override
    public SceneTextValidationOutcome validate(String originalText,
                                               String processedText) {
        List<String> failureReasons = new ArrayList<String>();
        Map<String, String> extractedContents =
                new LinkedHashMap<String, String>();

        validateSection(originalText, processedText,
                "重仓股变化", HEAVY_STOCK_CHANGES,
                Arrays.asList("一、重仓股变化", "二、基金经理操作解析"),
                extractedContents, failureReasons);
        validateSection(originalText, processedText,
                "基金行业变化", FUND_INDUSTRY_CHANGES,
                Arrays.asList("一、基金持仓变化", "二、基金经理操作解析"),
                extractedContents, failureReasons);
        validateSection(originalText, processedText,
                "仓位变化", POSITION_CHANGES,
                Arrays.asList("一、基金持仓变化", "二、基金经理操作解析"),
                extractedContents, failureReasons);

        return new SceneTextValidationOutcome(extractedContents,
                failureReasons);
    }

    private void validateSection(String originalText,
                                 String processedText,
                                 String tagName,
                                 String fieldName,
                                 List<String> allowedTitles,
                                 Map<String, String> extractedContents,
                                 List<String> failureReasons) {
        Optional<String> processedContent = extractUniqueContent(
                processedText, tagName, failureReasons);
        Optional<String> originalContent = extractUniqueContent(
                originalText, tagName, null);

        if (originalContent.isPresent()) {
            int length = originalContent.get().length();
            if (length < MIN_SECTION_LENGTH || length > MAX_SECTION_LENGTH) {
                failureReasons.add("<" + tagName + ">标签内的原始内容长度必须在"
                        + MIN_SECTION_LENGTH + "到" + MAX_SECTION_LENGTH
                        + "之间，实际为" + length);
            }
        } else if (processedContent.isPresent()) {
            failureReasons.add("无法从原始文本中唯一提取<" + tagName
                    + ">标签内容，不能计算原始内容长度");
        }

        if (!processedContent.isPresent()) {
            return;
        }
        String content = processedContent.get();
        extractedContents.put(fieldName, content);
        validateSummary(content, tagName, failureReasons);
        validateTitles(content, tagName, allowedTitles, failureReasons);
    }

    private Optional<String> extractUniqueContent(String text,
                                                  String tagName,
                                                  List<String> failureReasons) {
        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";
        int openCount = countOccurrences(text, openTag);
        int closeCount = countOccurrences(text, closeTag);
        if (openCount != 1 || closeCount != 1) {
            if (failureReasons != null) {
                failureReasons.add(openTag + closeTag
                        + "必须成对出现且只能出现一次，检测到开始标签"
                        + openCount + "次、结束标签" + closeCount + "次");
            }
            return Optional.empty();
        }
        int contentStart = text.indexOf(openTag) + openTag.length();
        int contentEnd = text.indexOf(closeTag);
        if (contentEnd < contentStart) {
            if (failureReasons != null) {
                failureReasons.add(openTag + closeTag + "的开始、结束顺序不正确");
            }
            return Optional.empty();
        }
        return Optional.of(text.substring(contentStart, contentEnd));
    }

    private void validateSummary(String content,
                                 String tagName,
                                 List<String> failureReasons) {
        int openCount = countOccurrences(content, SUMMARY_OPEN_TAG);
        int closeCount = countOccurrences(content, SUMMARY_CLOSE_TAG);
        if (openCount != 1 || closeCount != 1) {
            failureReasons.add("<" + tagName
                    + ">内的<summary></summary>必须成对出现且只能出现一次，检测到开始标签"
                    + openCount + "次、结束标签" + closeCount + "次");
            return;
        }

        int firstNonWhitespace = firstNonWhitespaceIndex(content);
        int summaryStart = content.indexOf(SUMMARY_OPEN_TAG);
        int summaryContentStart = summaryStart + SUMMARY_OPEN_TAG.length();
        int summaryEnd = content.indexOf(SUMMARY_CLOSE_TAG);
        if (summaryEnd < summaryContentStart) {
            failureReasons.add("<" + tagName
                    + ">内的<summary></summary>开始、结束顺序不正确");
            return;
        }
        if (firstNonWhitespace != summaryStart) {
            failureReasons.add("<" + tagName
                    + ">内首段文本必须使用<summary></summary>包裹");
        }
        String summary = content.substring(summaryContentStart, summaryEnd);
        if (summary.trim().isEmpty()) {
            failureReasons.add("<" + tagName + ">内的summary内容不能为空");
        }

        Matcher firstHeading = LEVEL_ONE_HEADING.matcher(content);
        if (firstHeading.find()
                && summaryEnd + SUMMARY_CLOSE_TAG.length() > firstHeading.start()) {
            failureReasons.add("<" + tagName
                    + ">内的summary必须完整出现在首个Markdown一级标题之前");
        }
    }

    private void validateTitles(String content,
                                String tagName,
                                List<String> allowedTitles,
                                List<String> failureReasons) {
        Set<String> allowedHeadingPrefixes = new LinkedHashSet<String>();
        for (String title : allowedTitles) {
            String headingPrefix = "# " + title;
            allowedHeadingPrefixes.add(headingPrefix);
            int count = countAllowedHeading(content, headingPrefix);
            if (count != 1) {
                failureReasons.add("<" + tagName
                        + ">内必须且只能出现一次一级标题“" + headingPrefix
                        + "”或“" + headingPrefix + "：副标题”，实际出现"
                        + count + "次");
            }
        }

        Matcher matcher = LEVEL_ONE_HEADING.matcher(content);
        while (matcher.find()) {
            String heading = stripLineEnding(matcher.group());
            if (!isAnyAllowedHeading(heading, allowedHeadingPrefixes)) {
                failureReasons.add("<" + tagName + ">内存在不允许的Markdown一级标题“"
                        + heading + "”");
            }
        }
    }

    private int countAllowedHeading(String content, String headingPrefix) {
        Matcher matcher = LEVEL_ONE_HEADING.matcher(content);
        int count = 0;
        while (matcher.find()) {
            String heading = stripLineEnding(matcher.group());
            if (isAllowedHeading(heading, headingPrefix)) {
                count++;
            }
        }
        return count;
    }

    private boolean isAnyAllowedHeading(
            String heading,
            Set<String> allowedHeadingPrefixes) {
        for (String prefix : allowedHeadingPrefixes) {
            if (isAllowedHeading(heading, prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllowedHeading(String heading, String headingPrefix) {
        if (heading.equals(headingPrefix)) {
            return true;
        }
        String subtitlePrefix = headingPrefix + "：";
        if (!heading.startsWith(subtitlePrefix)) {
            return false;
        }
        String subtitle = heading.substring(subtitlePrefix.length());
        return !subtitle.isEmpty() && subtitle.equals(subtitle.trim());
    }

    private int countOccurrences(String text, String target) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
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

    private String stripLineEnding(String value) {
        return value.endsWith("\r")
                ? value.substring(0, value.length() - 1) : value;
    }

}
