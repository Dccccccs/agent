package com.example.agentgateway;

import com.example.agentgateway.domain.filteredstream.CardDefinition;
import com.example.agentgateway.domain.filteredstream.LinkDefinition;
import com.example.agentgateway.domain.filteredstream.FundDefinition;
import com.example.agentgateway.domain.filteredstream.FundCardSource;
import com.example.agentgateway.domain.filteredstream.ProcessedContentBlock;
import com.example.agentgateway.domain.filteredstream.StreamMetadata;
import com.example.agentgateway.domain.filteredstream.StreamContentProcessingException;
import com.example.agentgateway.domain.filteredstream.StreamFallbackRequestedException;
import com.example.agentgateway.domain.filteredstream.StreamReplacementCatalog;
import com.example.agentgateway.domain.filteredstream.StreamingContentProcessor;
import com.example.agentgateway.domain.filteredstream.ThemeRiskFundInfo;
import com.example.agentgateway.domain.filteredstream.ThemeRiskTipInfo;
import com.example.agentgateway.domain.filteredstream.ThemeRiskUserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamingContentProcessorTest {
    private StreamingContentProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new StreamingContentProcessor(new TestCatalog());
        processor.registerMetadata(StreamMetadata.builder()
                .linkMapping(Arrays.asList(
                        link("000001", "基金名称"),
                        link("linkId", "基金名称")))
                .cardMapping(Arrays.asList(
                        card("card_xxx", "12343", "哈哈哈"),
                        card("card_risk", "risk-card", "风险卡片"),
                        card("card_return", "return-card", "收益卡片")))
                .build());
    }

    @Test
    void shouldHoldWholeFramesAndReplaceSensitiveWordAcrossFrames() {
        assertTrue(processor.accept("程度达到极").isEmpty());
        assertTrue(processor.accept("端").isEmpty());

        List<ProcessedContentBlock> result = processor.accept("高状态");

        assertEquals(1, result.size());
        assertEquals("程度达到较高状态", result.get(0).getContent());
        assertEquals("程度达到较高状态", processor.getTransformedAnswer());
    }

    @Test
    void shouldRestoreOriginalTextWhenSensitivePrefixDoesNotMatch() {
        assertTrue(processor.accept("程度达到极").isEmpty());

        List<ProcessedContentBlock> result = processor.accept("高水平");

        assertEquals(1, result.size());
        assertEquals("程度达到极高水平", result.get(0).getContent());
    }

    @Test
    void shouldUseLongestSensitiveWordAndSupportArbitraryPrefixes() {
        List<ProcessedContentBlock> first = processor.accept("当前为极端水平，");
        List<ProcessedContentBlock> second = processor.accept("存在风险事项");

        assertEquals("当前为超出常规水平，", first.get(0).getContent());
        assertEquals("存在一般事项", second.get(0).getContent());
    }

    @Test
    void shouldResolveCardTagAcrossFrames() {
        assertTrue(processor.accept("<m-card id='card_").isEmpty());
        List<ProcessedContentBlock> result = processor.accept("xxx' />");

        assertEquals(1, result.size());
        ProcessedContentBlock block = result.get(0);
        assertEquals("card", block.getType());
        assertEquals("<m-card card-id=\"12343\" card-name=\"哈哈哈\" "
                + "card-description=\"卡片描述\" card-data=\"{}\" />",
                block.getContent());
        assertEquals("12343", block.getCardMapping().get(0).getCardId());
        assertEquals("这是html文本", block.getCardMapping().get(0).getHtmlContent());
    }

    @Test
    void shouldRejectUnknownOrMalformedCardTag() {
        assertThrows(StreamContentProcessingException.class,
                () -> processor.accept("<m-card id='card_unknown' />"));

        StreamingContentProcessor malformed =
                new StreamingContentProcessor(new TestCatalog());
        assertThrows(StreamContentProcessingException.class,
                () -> malformed.accept("<m-card id='other' />"));
    }

    @Test
    void shouldResolveUrlAcrossFramesAndAttachMapping() {
        assertTrue(processor.accept("基金：<").isEmpty());
        assertTrue(processor.accept("url id=\"000001\"").isEmpty());
        List<ProcessedContentBlock> result = processor.accept(
                " text=\"模型输出名称\" />详情");

        assertEquals(1, result.size());
        ProcessedContentBlock block = result.get(0);
        assertEquals("text", block.getType());
        assertEquals("基金：<url id=\"000001\" text=\"基金名称\"/>详情",
                block.getContent());
        assertEquals("000001", block.getLinkMapping().get(0).getLinkId());
        assertEquals("基金名称",
                block.getLinkMapping().get(0).getLinkContent());
        assertEquals("000001",
                processor.getFirstFundReference().get().getFundId());
        assertEquals("基金名称",
                processor.getFirstFundReference().get().getText());
    }

    @Test
    void shouldResolveActualIdUrlAcrossFramesAndAttachMapping() {
        assertTrue(processor.accept("<url id=\"").isEmpty());
        assertTrue(processor.accept("linkId\" text").isEmpty());
        List<ProcessedContentBlock> result = processor.accept(
                "=\"基金名称\"/>");

        assertEquals(1, result.size());
        ProcessedContentBlock block = result.get(0);
        assertEquals("<url id=\"linkId\" text=\"基金名称\"/>",
                block.getContent());
        assertEquals("linkId", block.getLinkMapping().get(0).getLinkId());
        assertEquals("基金名称",
                block.getLinkMapping().get(0).getLinkContent());
    }

    @Test
    void shouldRejectLegacyFundIdUrlAttribute() {
        assertThrows(StreamContentProcessingException.class,
                () -> processor.accept(
                        "<url fundId=\"000001\" text=\"基金名称\" />"));
    }

    @Test
    void shouldHandleCardNamesFromActualUpstreamFrames() {
        assertTrue(processor.accept("建议查看<m-card id='").isEmpty());
        List<ProcessedContentBlock> risk = processor.accept("card_risk' />");
        assertEquals(2, risk.size());
        assertEquals("建议查看", risk.get(0).getContent());
        assertEquals("risk-card", risk.get(1).getCardMapping().get(0).getCardId());

        assertTrue(processor.accept("和<m-card id='card_return").isEmpty());
        List<ProcessedContentBlock> returns = processor.accept("' />。");
        assertEquals(3, returns.size());
        assertEquals("和", returns.get(0).getContent());
        assertEquals("return-card", returns.get(1).getCardMapping().get(0).getCardId());
        assertEquals("。", returns.get(2).getContent());
    }

    @Test
    void shouldRejectMultipleUrlsInOneInputFrame() {
        String tag = "<url id=\"000001\" text=\"基金名称\" />";

        StreamContentProcessingException exception = assertThrows(
                StreamContentProcessingException.class,
                () -> processor.accept(tag + tag));

        assertTrue(exception.getMessage().contains("多个"));
        assertEquals("", processor.getTransformedAnswer());
    }

    @Test
    void shouldRejectUnclosedStructuredContentAtEnd() {
        assertTrue(processor.accept("正文<m-card id='card_xxx").isEmpty());

        assertThrows(StreamContentProcessingException.class, processor::finish);
        assertEquals("", processor.getTransformedAnswer());
    }

    @Test
    void shouldFlushUnmatchedSensitivePrefixAndPlainAngleBracketAtEnd() {
        StreamingContentProcessor plainProcessor =
                new StreamingContentProcessor(new TestCatalog());
        plainProcessor.registerMetadata(StreamMetadata.empty());
        assertTrue(plainProcessor.accept("普通极<").isEmpty());

        List<ProcessedContentBlock> result = plainProcessor.finish();

        assertEquals(1, result.size());
        assertEquals("普通极<", result.get(0).getContent());
    }

    @Test
    void shouldRequireEveryMetadataMappingExactlyOnceAtEnd() {
        StreamingContentProcessor complete =
                new StreamingContentProcessor(new TestCatalog());
        complete.registerMetadata(StreamMetadata.builder()
                .link(link("000001", "基金名称"))
                .card(card("card_xxx", "12343", "哈哈哈"))
                .build());

        complete.accept("<url id=\"000001\" text=\"模型名称\" />");
        complete.accept("<m-card id='card_xxx' />");

        assertTrue(complete.finish().isEmpty());
    }

    @Test
    void shouldRejectMissingLinkMappingAtEnd() {
        StreamingContentProcessor missingLink =
                new StreamingContentProcessor(new TestCatalog());
        missingLink.registerMetadata(StreamMetadata.builder()
                .link(link("000001", "基金名称"))
                .build());
        missingLink.accept("只有普通正文");

        StreamContentProcessingException exception = assertThrows(
                StreamContentProcessingException.class, missingLink::finish);

        assertTrue(exception.getMessage().contains("linkMapping 数量不一致"));
        assertTrue(exception.getMessage().contains("000001"));
    }

    @Test
    void shouldRejectMissingCardMappingAtEnd() {
        StreamingContentProcessor missingCard =
                new StreamingContentProcessor(new TestCatalog());
        missingCard.registerMetadata(StreamMetadata.builder()
                .card(card("card_xxx", "12343", "哈哈哈"))
                .build());
        missingCard.accept("只有普通正文");

        StreamContentProcessingException exception = assertThrows(
                StreamContentProcessingException.class, missingCard::finish);

        assertTrue(exception.getMessage().contains("cardMapping 数量不一致"));
        assertTrue(exception.getMessage().contains("card_xxx"));
    }

    @Test
    void shouldRejectRepeatedLinkAcrossFramesImmediately() {
        StreamingContentProcessor repeatedLink =
                new StreamingContentProcessor(new TestCatalog());
        repeatedLink.registerMetadata(StreamMetadata.builder()
                .link(link("000001", "基金名称"))
                .build());
        String tag = "<url id=\"000001\" text=\"基金名称\" />";
        repeatedLink.accept(tag);

        StreamContentProcessingException exception = assertThrows(
                StreamContentProcessingException.class,
                () -> repeatedLink.accept(tag));

        assertTrue(exception.getMessage().contains("重复出现 linkId"));
    }

    @Test
    void shouldRejectRepeatedCardAcrossFramesImmediately() {
        StreamingContentProcessor repeatedCard =
                new StreamingContentProcessor(new TestCatalog());
        repeatedCard.registerMetadata(StreamMetadata.builder()
                .card(card("card_xxx", "12343", "哈哈哈"))
                .build());
        String tag = "<m-card id='card_xxx' />";
        repeatedCard.accept(tag);

        StreamContentProcessingException exception = assertThrows(
                StreamContentProcessingException.class,
                () -> repeatedCard.accept(tag));

        assertTrue(exception.getMessage().contains("重复出现卡片占位符"));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void shouldCollectFundItemsInsideCardAcrossFrames(CapturedOutput output) {
        StreamingContentProcessor fundProcessor =
                new StreamingContentProcessor(new TestCatalog());
        fundProcessor.registerMetadata(StreamMetadata.builder()
                .fundList(FundCardSource.builder()
                        .subtitle("数据更新日期：2026.8.25")
                        .list(Arrays.asList(fund("000001", "基金一"),
                                fund("000002", "基金二")))
                        .build())
                .build());

        assertTrue(fundProcessor.accept(
                "前文<m-ca").isEmpty());
        assertTrue(fundProcessor.accept(
                "rd id=''><li id='000001' text='' />\n<li id='000002'")
                .isEmpty());
        assertTrue(fundProcessor.accept(
                " text='模型描述' /></m-ca").isEmpty());
        assertFalse(output.getOut().contains("基金卡片 li 列表接收耗时"));
        List<ProcessedContentBlock> blocks = fundProcessor.accept(
                "rd>后文");

        assertEquals(3, blocks.size());
        assertEquals("前文", blocks.get(0).getContent());
        assertTrue(blocks.get(1).isFundCardGroup());
        assertEquals("数据更新日期：2026.8.25",
                blocks.get(1).getFundCardSource().getSubtitle());
        assertEquals(2, blocks.get(1).getFundCardSource().getList().size());
        assertEquals("000001",
                blocks.get(1).getFundCardSource().getList().get(0).getFundId());
        assertEquals("", blocks.get(1).getFundCardSource().getList().get(0).getFundDesc());
        assertEquals("000002",
                blocks.get(1).getFundCardSource().getList().get(1).getFundId());
        assertEquals("模型描述",
                blocks.get(1).getFundCardSource().getList().get(1).getFundDesc());
        assertEquals(Arrays.asList("000001", "000002"),
                fundProcessor.getRecommendedFundIds());
        assertEquals(Arrays.asList("000001", "000002"),
                fundProcessor.getFirstFundItemIds());
        assertEquals("后文", blocks.get(2).getContent());
        assertTrue(output.getOut().contains(
                "基金卡片 li 列表接收耗时, elapsedMillis="));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void shouldNotTimeSelfClosingMetadataCard(CapturedOutput output) {
        processor.accept("<m-card id='card_xxx' />");

        assertFalse(output.getOut().contains("基金卡片 li 列表接收耗时"));
    }

    @Test
    void shouldKeepMetadataFundDescriptionWhenTextAttributeIsAbsent() {
        StreamingContentProcessor fundProcessor =
                new StreamingContentProcessor(new TestCatalog());
        fundProcessor.registerMetadata(StreamMetadata.builder()
                .fundList(fundList(fund("000001", "基金一")))
                .build());

        assertTrue(fundProcessor.accept(
                "<m-card><li id='000001' />").isEmpty());
        List<ProcessedContentBlock> blocks = fundProcessor.accept("</m-card>");

        assertEquals(1, blocks.size());
        assertTrue(blocks.get(0).isFundCardGroup());
        assertEquals("基金一描述",
                blocks.get(0).getFundCardSource().getList().get(0).getFundDesc());
    }

    @Test
    void shouldWaitForClosingCardTagAfterLastFundItem() {
        StreamingContentProcessor fundProcessor =
                new StreamingContentProcessor(new TestCatalog());
        fundProcessor.registerMetadata(StreamMetadata.builder()
                .fundList(fundList(fund("000001", "基金一")))
                .build());

        assertTrue(fundProcessor.accept(
                "<m-card><li id=\"000001\" text=\"模型描述\" />")
                .isEmpty());

        assertThrows(StreamContentProcessingException.class,
                fundProcessor::finish);
    }

    @Test
    void shouldProduceFundCardOnlyAfterClosingTag() {
        StreamingContentProcessor fundProcessor =
                new StreamingContentProcessor(new TestCatalog());
        fundProcessor.registerMetadata(StreamMetadata.builder()
                .fundList(fundList(fund("000001", "基金一")))
                .build());

        assertTrue(fundProcessor.accept(
                "<m-card><li id=\"000001\" text=\"模型描述\" />")
                .isEmpty());
        List<ProcessedContentBlock> blocks = fundProcessor.accept("</m-card>");

        assertEquals(1, blocks.size());
        assertTrue(blocks.get(0).isFundCardGroup());
        assertEquals("000001", blocks.get(0).getFundCardSource().getList()
                .get(0).getFundId());
    }

    @Test
    void shouldReplaceRiskTipsAcrossFramesUsingOnlyFundCardItems() {
        StreamingContentProcessor fundProcessor =
                new StreamingContentProcessor(Collections::emptyMap);
        fundProcessor.registerMetadata(StreamMetadata.builder()
                .linkMapping(Collections.singletonList(link("000003", "链接基金")))
                .fundList(fundList(fund("000001", "基金一"),
                        fund("000002", "基金二")))
                .themeRiskTipInfo(ThemeRiskTipInfo.builder()
                        .userInfo(ThemeRiskUserInfo.builder()
                                .riskLevel("C4")
                                .growthStyleStatus("NOT_CONFIG")
                                .balanceStyleStatus("OVER")
                                .build())
                        .fundList(Arrays.asList(
                                themeFund("000001", "R2", "成长"),
                                themeFund("000002", "R2", "均衡"),
                                themeFund("000003", "R5", "成长")))
                        .build())
                .build());

        assertEquals(1, fundProcessor.accept(
                "<m-card><li id='000001' /></m-card>").size());
        fundProcessor.accept("<url id=\"000003\" text=\"链接基金\" />");
        assertTrue(fundProcessor.accept(
                "<m-card><li id='000002' />").isEmpty());
        assertTrue(fundProcessor.accept("</m-card>推荐<risk-").isEmpty());
        List<ProcessedContentBlock> result = fundProcessor.accept("tips />后文");

        assertEquals(2, result.size());
        assertTrue(result.get(0).isFundCardGroup());
        assertEquals("推荐上述基金为均衡风格基金，你目前在均衡风格上"
                        + "配置比例较高，建议优化风格比例，分散风险。后文",
                result.get(1).getContent());
        assertEquals(Arrays.asList("000001", "000002"),
                fundProcessor.getRecommendedFundIds());
    }

    @Test
    void shouldRemoveRiskTipsMarkerWhenMetadataIsAbsent() {
        StreamingContentProcessor fundProcessor =
                new StreamingContentProcessor(Collections::emptyMap);
        fundProcessor.registerMetadata(StreamMetadata.empty());

        assertTrue(fundProcessor.accept("前文<risk-").isEmpty());
        List<ProcessedContentBlock> result = fundProcessor.accept("tips />后文");

        assertEquals(1, result.size());
        assertEquals("前文后文", result.get(0).getContent());
    }

    @Test
    void shouldDeduplicateFundsWithinOneContinuousGroup() {
        StreamingContentProcessor fundProcessor =
                new StreamingContentProcessor(new TestCatalog());
        fundProcessor.registerMetadata(StreamMetadata.builder()
                .fundList(fundList(fund("000001", "基金一")))
                .build());

        List<ProcessedContentBlock> blocks = fundProcessor.accept(
                "<m-card><li id=\"000001\" text=\"\" />"
                        + "<li id=\"000001\" text=\"重复\" /></m-card>");

        assertEquals(1, blocks.size());
        assertTrue(blocks.get(0).isFundCardGroup());
        assertEquals(1, blocks.get(0).getFundCardSource().getList().size());
    }

    @Test
    void shouldRejectFundItemNotDeclaredInMetadata() {
        StreamingContentProcessor fundProcessor =
                new StreamingContentProcessor(new TestCatalog());
        fundProcessor.registerMetadata(StreamMetadata.empty());

        StreamContentProcessingException exception = assertThrows(
                StreamContentProcessingException.class,
                () -> fundProcessor.accept(
                        "<m-card><li id='999999' text='未知基金' /></m-card>"));

        assertTrue(exception.getMessage().contains("fundList"));
    }

    @Test
    void shouldRejectFundItemOutsideCardContainer() {
        assertThrows(StreamContentProcessingException.class,
                () -> processor.accept("<li id='000001' />"));
    }

    @Test
    void shouldKeepHouseviewMarkupAsText() {
        List<ProcessedContentBlock> result = processor.accept(
                "<Houseview>普通内容</Houseview>");

        assertEquals(1, result.size());
        assertEquals("<Houseview>普通内容</Houseview>", result.get(0).getContent());
    }

    @Test
    void shouldHoldSplitFallbackMarkerWithoutLeakingCurrentFrame() {
        assertTrue(processor.accept("未输出的正文<fall").isEmpty());

        assertThrows(StreamFallbackRequestedException.class,
                () -> processor.accept("back />不应输出"));
        assertEquals("", processor.getTransformedAnswer());
    }

    @Test
    void shouldKeepPreviouslyReleasedTextWhenFallbackMarkerAppears() {
        assertEquals("已输出。", processor.accept("已输出。").get(0).getContent());

        assertThrows(StreamFallbackRequestedException.class,
                () -> processor.accept("本帧正文<fallback />尾部"));
        assertEquals("已输出。", processor.getTransformedAnswer());
    }

    @Test
    void shouldRestoreTextThatOnlyResemblesFallbackMarker() {
        assertTrue(processor.accept("普通<fall").isEmpty());

        List<ProcessedContentBlock> result = processor.accept("back!正文");

        assertEquals(1, result.size());
        assertEquals("普通<fallback!正文", result.get(0).getContent());
    }

    @Test
    void shouldDetectFallbackMarkerAfterFundGroupWithoutCreatingCard() {
        StreamingContentProcessor fundProcessor =
                new StreamingContentProcessor(new TestCatalog());
        fundProcessor.registerMetadata(StreamMetadata.builder()
                .fundList(fundList(fund("000001", "基金一")))
                .build());

        assertTrue(fundProcessor.accept("<m-card><li id='000001' />\n<fall")
                .isEmpty());
        assertThrows(StreamFallbackRequestedException.class,
                () -> fundProcessor.accept("back />"));
        assertEquals("", fundProcessor.getTransformedAnswer());
    }

    private CardDefinition card(String placeholder, String id, String name) {
        return CardDefinition.builder()
                .cardPlaceHolder(placeholder)
                .cardId(id)
                .cardName(name)
                .cardDescription("卡片描述")
                .htmlContent("这是html文本")
                .build();
    }

    private LinkDefinition link(String id, String content) {
        return LinkDefinition.builder()
                .linkId(id)
                .linkContent(content)
                .build();
    }

    private FundDefinition fund(String id, String name) {
        return FundDefinition.builder()
                .fundId(id)
                .fundName(name)
                .fundDesc(name + "描述")
                .build();
    }

    private FundCardSource fundList(FundDefinition... funds) {
        return FundCardSource.builder()
                .subtitle("")
                .list(Arrays.asList(funds))
                .build();
    }

    private ThemeRiskFundInfo themeFund(String id, String risk, String style) {
        return ThemeRiskFundInfo.builder()
                .fundId(id)
                .riskLevel(risk)
                .fundType(style)
                .build();
    }

    private static class TestCatalog implements StreamReplacementCatalog {
        @Override
        public Map<String, String> sensitiveReplacements() {
            Map<String, String> replacements = new LinkedHashMap<String, String>();
            replacements.put("极端高", "较高");
            replacements.put("极端水平", "超出常规水平");
            replacements.put("极度", "非常");
            replacements.put("风险", "一般");
            return replacements;
        }

    }
}
