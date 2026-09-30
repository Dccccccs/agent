package com.example.agentgateway;

import com.example.agentgateway.domain.filteredstream.CardDefinition;
import com.example.agentgateway.domain.filteredstream.LinkDefinition;
import com.example.agentgateway.domain.filteredstream.ProcessedContentBlock;
import com.example.agentgateway.domain.filteredstream.StreamMetadata;
import com.example.agentgateway.domain.filteredstream.StreamContentProcessingException;
import com.example.agentgateway.domain.filteredstream.StreamReplacementCatalog;
import com.example.agentgateway.domain.filteredstream.StreamingContentProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void shouldKeepHouseviewMarkupAsText() {
        List<ProcessedContentBlock> result = processor.accept(
                "<Houseview>普通内容</Houseview>");

        assertEquals(1, result.size());
        assertEquals("<Houseview>普通内容</Houseview>", result.get(0).getContent());
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
