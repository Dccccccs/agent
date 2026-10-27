package com.example.agentgateway.domain.filteredstream;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** 根据已输出的基金卡片中的 li，生成 <risk-tips /> 对应的话术。 */
@Slf4j
public class ThemeRiskTipResolver {
    private static final String OVER = "OVER";
    private static final String[] A_CUSTOMER_NAMES = {
            "保守型", "谨慎型", "稳健型", "进取型", "激进型"};
    private static final String[] C_CUSTOMER_NAMES = {
            "谨慎型", "稳健型", "平衡型", "进取型", "激进型"};

    public String resolve(ThemeRiskTipInfo source, Set<String> fundIds) {
        if (source == null) {
            log.info("主题风险话术未输出: themeRiskTipInfo 缺失");
            return "";
        }
        if (source.getUserInfo() == null) {
            log.info("主题风险话术未输出: themeRiskTipInfo.userInfo 缺失");
            return "";
        }
        if (source.getFundList() == null || source.getFundList().isEmpty()) {
            log.info("主题风险话术未输出: themeRiskTipInfo.fundList 缺失或为空");
            return "";
        }
        if (fundIds == null || fundIds.isEmpty()) {
            log.info("主题风险话术未输出: 答案中没有完整卡片内的基金 li");
            return "";
        }
        String customerRiskLevel = source.getUserInfo().getRiskLevel();
        int customerLevel = customerLevel(customerRiskLevel);
        if (customerLevel < 1) {
            log.info("主题风险话术未输出: 客户风评等级不是 A1-A5 或 C1-C5");
            return "";
        }
        List<ThemeRiskFundInfo> matched = new ArrayList<ThemeRiskFundInfo>();
        for (ThemeRiskFundInfo fund : source.getFundList()) {
            if (fund != null && fundIds.contains(fund.getFundId())) {
                matched.add(fund);
            }
        }
        if (matched.isEmpty()) {
            log.info("主题风险话术未输出: 答案基金未匹配 themeRiskTipInfo.fundList, "
                    + "answerFundCount={}, metadataFundCount={}",
                    fundIds.size(), source.getFundList().size());
            return "";
        }

        // 超出客户风评的产品先给出更重要的风险提示；低风险客户不生成风格提示。
        String higherRisk = higherRiskTip(customerRiskLevel, customerLevel,
                matched);
        if (!higherRisk.isEmpty()) {
            return higherRisk;
        }
        // A4/A5 与 C4/C5 规则相同：私募客户只适用第 3 类超风评提示。
        if (customerLevel < 4) {
            log.info("主题风险话术未输出: 低等级客户无超风评基金, "
                    + "customerRiskLevel={}, matchedFundCount={}",
                    customerRiskLevel, matched.size());
            return "";
        }
        if (Boolean.TRUE.equals(source.getUserInfo().getIsPrivate())) {
            log.info("主题风险话术未输出: 私募客户仅适用超风评提示，"
                    + "但匹配基金中无超风评基金, customerRiskLevel={}, "
                    + "matchedFundCount={}", customerRiskLevel, matched.size());
            return "";
        }
        return styleTip(source.getUserInfo(), matched);
    }

    private String higherRiskTip(String customerRiskLevel, int customerLevel,
                                 List<ThemeRiskFundInfo> matched) {
        Set<Integer> riskLevels = new TreeSet<Integer>();
        for (ThemeRiskFundInfo fund : matched) {
            int productLevel = level(fund.getRiskLevel(), 'R');
            if (productLevel > customerLevel) {
                riskLevels.add(productLevel);
            }
        }
        if (riskLevels.isEmpty()) {
            return "";
        }
        List<String> descriptions = new ArrayList<String>();
        for (Integer riskLevel : riskLevels) {
            descriptions.add("R" + riskLevel + riskName(riskLevel));
        }
        String products = String.join("、", descriptions);
        if (customerLevel >= 4) {
            return "上述基金为" + products
                    + "产品，波动较大，可能超出你的风险承受能力。"
                    + "建议充分了解产品详情和风险，结合自身情况谨慎决策。";
        }
        return "你当前风险承受能力为" + customerRiskLevel
                + customerName(customerRiskLevel, customerLevel)
                + "，上述基金多为" + products
                + "产品，波动较大，超出你的风险承受能力。"
                + "建议充分了解产品详情和风险，结合自身情况谨慎决策。";
    }

    private String styleTip(ThemeRiskUserInfo user,
                            List<ThemeRiskFundInfo> matched) {
        Set<String> overStyles = new LinkedHashSet<String>();
        Set<String> otherStyles = new LinkedHashSet<String>();
        for (ThemeRiskFundInfo fund : matched) {
            String style = fund.getFundType();
            String status = styleStatus(user, style);
            if (status == null) {
                continue;
            }
            (OVER.equals(status) ? overStyles : otherStyles).add(style);
        }
        // A4/A5、C4/C5 只输出一类话术：超风评 > 超配风格 > 非超配风格。
        if (!overStyles.isEmpty()) {
            String styles = styleNames(overStyles);
            return "上述基金为" + styles + "基金，你目前在"
                    + styles + "上配置比例较高，建议优化风格比例，分散风险。";
        }
        if (!otherStyles.isEmpty()) {
            return "上述基金为" + styleNames(otherStyles)
                    + "基金，建议结合自身风险承受能力和资金安排，"
                    + "充分了解产品详情和风险后适当配置。";
        }
        log.info("主题风险话术未输出: 匹配基金无可用的价值、均衡或成长风格配置状态, "
                + "matchedFundCount={}", matched.size());
        return "";
    }

    private String styleNames(Set<String> styles) {
        List<String> names = new ArrayList<String>();
        for (String style : styles) {
            names.add(style + "风格");
        }
        return String.join("、", names);
    }

    private String styleStatus(ThemeRiskUserInfo user, String style) {
        if ("价值".equals(style)) {
            return user.getValueStyleStatus();
        }
        if ("均衡".equals(style)) {
            return user.getBalanceStyleStatus();
        }
        if ("成长".equals(style)) {
            return user.getGrowthStyleStatus();
        }
        return null;
    }

    private int level(String value, char prefix) {
        if (value == null || value.length() != 2
                || value.charAt(0) != prefix
                || value.charAt(1) < '1' || value.charAt(1) > '5') {
            return -1;
        }
        return value.charAt(1) - '0';
    }

    private int customerLevel(String value) {
        if (value == null || value.length() != 2
                || (value.charAt(0) != 'A' && value.charAt(0) != 'C')) {
            return -1;
        }
        return level(value, value.charAt(0));
    }

    private String riskName(int level) {
        switch (level) {
            case 1: return "低风险";
            case 2: return "中低风险";
            case 3: return "中风险";
            case 4: return "中高风险";
            case 5: return "高风险";
            default: return "";
        }
    }

    private String customerName(String riskLevel, int level) {
        return riskLevel.charAt(0) == 'A'
                ? A_CUSTOMER_NAMES[level - 1] : C_CUSTOMER_NAMES[level - 1];
    }
}
