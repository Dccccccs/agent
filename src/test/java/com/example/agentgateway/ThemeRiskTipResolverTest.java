package com.example.agentgateway;

import com.example.agentgateway.domain.filteredstream.ThemeRiskFundInfo;
import com.example.agentgateway.domain.filteredstream.ThemeRiskTipInfo;
import com.example.agentgateway.domain.filteredstream.ThemeRiskTipResolver;
import com.example.agentgateway.domain.filteredstream.ThemeRiskUserInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class ThemeRiskTipResolverTest {
    private final ThemeRiskTipResolver resolver = new ThemeRiskTipResolver();

    @Test
    void shouldPrioritizeHigherRiskOverStylesForC4() {
        ThemeRiskTipInfo info = info("C4", "OVER", "OVER",
                fund("001", "R3", "成长"),
                fund("002", "R5", "均衡"),
                fund("003", "R5", ""));

        assertEquals("上述基金为R5高风险产品，波动较大，可能超出你的风险承受能力。"
                        + "建议充分了解产品详情和风险，结合自身情况谨慎决策。",
                resolver.resolve(info, ids("001", "002", "003")));
    }

    @Test
    void shouldChooseOverAllocatedStylesBeforeOtherStyles() {
        ThemeRiskTipInfo info = info("C5", "OVER", "NOT_CONFIG",
                fund("001", "R2", "成长"),
                fund("002", "R2", "均衡"),
                fund("003", "R2", "成长"));

        assertEquals("上述基金为成长风格基金，你目前在成长风格上配置比例较高，"
                        + "建议优化风格比例，分散风险。",
                resolver.resolve(info, ids("001", "002", "003")));
    }

    @Test
    void shouldListMultipleNonOverAllocatedStyles() {
        ThemeRiskTipInfo info = info("C5", "NOT_CONFIG", "UNDER",
                fund("001", "R2", "成长"),
                fund("002", "R2", "均衡"));

        assertEquals("上述基金为成长风格、均衡风格基金，"
                        + "建议结合自身风险承受能力和资金安排，"
                        + "充分了解产品详情和风险后适当配置。",
                resolver.resolve(info, ids("001", "002")));
    }

    @Test
    void shouldListOnlyProductsAboveC3RiskLevel() {
        ThemeRiskTipInfo info = info("C3", "OVER", "OVER",
                fund("001", "R3", "成长"),
                fund("002", "R5", "均衡"),
                fund("003", "R4", ""));

        assertEquals("你当前风险承受能力为C3平衡型，上述基金多为"
                        + "R4中高风险、R5高风险产品，波动较大，超出你的风险承受能力。"
                        + "建议充分了解产品详情和风险，结合自身情况谨慎决策。",
                resolver.resolve(info, ids("001", "002", "003")));
    }

    @Test
    void shouldReturnEmptyWhenMetadataOrApplicableFundsAreMissing() {
        ThemeRiskTipInfo info = info("C3", "OVER", "OVER",
                fund("001", "R2", "成长"));

        assertEquals("", resolver.resolve(null, ids("001")));
        assertEquals("", resolver.resolve(info, ids("999")));
        assertEquals("", resolver.resolve(info, ids("001")));
        assertEquals("", resolver.resolve(info, Collections.<String>emptySet()));
    }

    @Test
    void shouldKeepOnlyHigherRiskBranchForPrivateC4AndC5() {
        ThemeRiskTipInfo c4HigherRisk = info("C4", true, "OVER", "OVER",
                fund("001", "R5", "成长"));
        ThemeRiskTipInfo c4StyleOnly = info("C4", true, "OVER", "OVER",
                fund("001", "R4", "成长"));
        ThemeRiskTipInfo c5StyleOnly = info("C5", true, "OVER", "OVER",
                fund("001", "R5", "成长"));

        assertEquals("上述基金为R5高风险产品，波动较大，可能超出你的风险承受能力。"
                        + "建议充分了解产品详情和风险，结合自身情况谨慎决策。",
                resolver.resolve(c4HigherRisk, ids("001")));
        assertEquals("", resolver.resolve(c4StyleOnly, ids("001")));
        assertEquals("", resolver.resolve(c5StyleOnly, ids("001")));
    }

    @Test
    void shouldApplyC1ToC3RulesToA1ToA3AndKeepOriginalPrefix() {
        String[] names = {"保守型", "谨慎型", "稳健型"};
        for (int level = 1; level <= 3; level++) {
            ThemeRiskTipInfo info = info("A" + level, "OVER", "OVER",
                    fund("001", "R4", "成长"));

            assertEquals("你当前风险承受能力为A" + level + names[level - 1]
                            + "，上述基金多为R4中高风险产品，波动较大，"
                            + "超出你的风险承受能力。建议充分了解产品详情和风险，"
                            + "结合自身情况谨慎决策。",
                    resolver.resolve(info, ids("001")));
        }
    }

    @Test
    void shouldUseDistinctChineseNamesForC1ToC3() {
        String[] names = {"谨慎型", "稳健型", "平衡型"};
        for (int level = 1; level <= 3; level++) {
            ThemeRiskTipInfo info = info("C" + level, "OVER", "OVER",
                    fund("001", "R4", "成长"));

            assertEquals("你当前风险承受能力为C" + level + names[level - 1]
                            + "，上述基金多为R4中高风险产品，波动较大，"
                            + "超出你的风险承受能力。建议充分了解产品详情和风险，"
                            + "结合自身情况谨慎决策。",
                    resolver.resolve(info, ids("001")));
        }
    }

    @Test
    void shouldApplyC4AndC5RulesToA4AndA5() {
        ThemeRiskTipInfo a4HigherRisk = info("A4", "OVER", "OVER",
                fund("001", "R5", "成长"));
        ThemeRiskTipInfo a4StyleOnly = info("A4", "OVER", "OVER",
                fund("001", "R4", "成长"));
        ThemeRiskTipInfo a5StyleOnly = info("A5", "OVER", "OVER",
                fund("001", "R5", "成长"));

        assertEquals("上述基金为R5高风险产品，波动较大，可能超出你的风险承受能力。"
                        + "建议充分了解产品详情和风险，结合自身情况谨慎决策。",
                resolver.resolve(a4HigherRisk, ids("001")));
        assertEquals("上述基金为成长风格基金，你目前在成长风格上配置比例较高，"
                        + "建议优化风格比例，分散风险。",
                resolver.resolve(a4StyleOnly, ids("001")));
        assertEquals("上述基金为成长风格基金，你目前在成长风格上配置比例较高，"
                        + "建议优化风格比例，分散风险。",
                resolver.resolve(a5StyleOnly, ids("001")));
    }

    @Test
    void shouldKeepOnlyHigherRiskBranchForPrivateA4AndA5() {
        ThemeRiskTipInfo a4HigherRisk = info("A4", true, "OVER", "OVER",
                fund("001", "R5", "成长"));
        ThemeRiskTipInfo a4StyleOnly = info("A4", true, "OVER", "OVER",
                fund("001", "R4", "成长"));
        ThemeRiskTipInfo a5StyleOnly = info("A5", true, "OVER", "OVER",
                fund("001", "R5", "成长"));

        assertEquals("上述基金为R5高风险产品，波动较大，可能超出你的风险承受能力。"
                        + "建议充分了解产品详情和风险，结合自身情况谨慎决策。",
                resolver.resolve(a4HigherRisk, ids("001")));
        assertEquals("", resolver.resolve(a4StyleOnly, ids("001")));
        assertEquals("", resolver.resolve(a5StyleOnly, ids("001")));
    }

    @Test
    void shouldLogReasonsWhenNoRiskTipIsProduced(CapturedOutput output) {
        ThemeRiskTipInfo c3 = info("C3", "OVER", "OVER",
                fund("001", "R2", "成长"));
        ThemeRiskTipInfo privateA4 = info("A4", true, "OVER", "OVER",
                fund("001", "R4", "成长"));
        ThemeRiskTipInfo noStyle = info("C5", "OVER", "OVER",
                fund("001", "R2", ""));
        ThemeRiskTipInfo noUser = ThemeRiskTipInfo.builder()
                .fundList(Arrays.asList(fund("001", "R2", "成长")))
                .build();
        ThemeRiskTipInfo noFundList = ThemeRiskTipInfo.builder()
                .userInfo(ThemeRiskUserInfo.builder().riskLevel("C3").build())
                .build();

        resolver.resolve(null, ids("001"));
        resolver.resolve(noUser, ids("001"));
        resolver.resolve(noFundList, ids("001"));
        resolver.resolve(c3, Collections.<String>emptySet());
        resolver.resolve(info("B3", "OVER", "OVER",
                fund("001", "R2", "成长")), ids("001"));
        resolver.resolve(c3, ids("999"));
        resolver.resolve(c3, ids("001"));
        resolver.resolve(privateA4, ids("001"));
        resolver.resolve(noStyle, ids("001"));

        assertTrue(output.getOut().contains("themeRiskTipInfo 缺失"));
        assertTrue(output.getOut().contains("themeRiskTipInfo.userInfo 缺失"));
        assertTrue(output.getOut().contains("themeRiskTipInfo.fundList 缺失或为空"));
        assertTrue(output.getOut().contains("答案中没有完整卡片内的基金 li"));
        assertTrue(output.getOut().contains("客户风评等级不是 A1-A5 或 C1-C5"));
        assertTrue(output.getOut().contains("答案基金未匹配"));
        assertTrue(output.getOut().contains("低等级客户无超风评基金"));
        assertTrue(output.getOut().contains("私募客户仅适用超风评提示"));
        assertTrue(output.getOut().contains("无可用的价值、均衡或成长风格配置状态"));
    }

    private ThemeRiskTipInfo info(String level, String growth, String balance,
                                  ThemeRiskFundInfo... funds) {
        return info(level, false, growth, balance, funds);
    }

    private ThemeRiskTipInfo info(String level, boolean isPrivate,
                                  String growth, String balance,
                                  ThemeRiskFundInfo... funds) {
        return ThemeRiskTipInfo.builder()
                .userInfo(ThemeRiskUserInfo.builder()
                        .riskLevel(level)
                        .isPrivate(isPrivate)
                        .growthStyleStatus(growth)
                        .balanceStyleStatus(balance)
                        .build())
                .fundList(Arrays.asList(funds))
                .build();
    }

    private ThemeRiskFundInfo fund(String id, String level, String style) {
        return ThemeRiskFundInfo.builder()
                .fundId(id)
                .riskLevel(level)
                .fundType(style)
                .build();
    }

    private LinkedHashSet<String> ids(String... values) {
        return new LinkedHashSet<String>(Arrays.asList(values));
    }
}
