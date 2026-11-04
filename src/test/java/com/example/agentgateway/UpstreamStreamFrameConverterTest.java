package com.example.agentgateway;

import com.example.agentgateway.application.filteredstream.FilteredStreamException;
import com.example.agentgateway.application.filteredstream.model.UpstreamStreamFrame;
import com.example.agentgateway.infrastructure.filteredstream.client.UpstreamStreamFrameConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpstreamStreamFrameConverterTest {
    private UpstreamStreamFrameConverter converter;

    @BeforeEach
    void setUp() {
        converter = new UpstreamStreamFrameConverter(new ObjectMapper());
    }

    @Test
    void shouldSkipEmptySseData() {
        assertFalse(converter.convert("data: ").isDeliverable());
    }

    @Test
    void shouldDecodeStatusFrameWithDataPrefix() {
        UpstreamStreamFrame frame = converter.convert(
                "data: {\"status\":\"MIDDLE\",\"content\":\"增量\"}");

        assertEquals("增量", frame.getContent());
        assertFalse(frame.getDone());
    }

    @Test
    void shouldRejectNonStatusFormats() {
        assertThrows(FilteredStreamException.class,
                () -> converter.convert("普通增量文本"));
        assertThrows(FilteredStreamException.class,
                () -> converter.convert("{\"choices\":[{\"delta\":{\"content\":\"增量\"}}]}"));
        assertThrows(FilteredStreamException.class,
                () -> converter.convert("{\"returnType\":\"DATA\"}"));
    }

    @Test
    void shouldRejectUnknownStatusInsteadOfLeakingRawJson() {
        FilteredStreamException exception = assertThrows(
                FilteredStreamException.class,
                () -> converter.convert(
                        "{\"status\":\"UNKNOW\",\"content\":\"不应输出\"}"));

        assertTrue(exception.getMessage().contains("UNKNOW"));
    }

    @Test
    void shouldNormalizeActualUpstreamMetadataContentAsBodyObject() throws Exception {
        // 【真实首帧结构模拟】content 是 JSON 字符串，解码后会变成 body 对象，
        // 同时构建仅供后续 URL/卡片校验使用的 StreamMetadata。
        String raw = "{\"label\":\"首帧数据\",\"status\":\"METADATA\","
                + "\"content\":\"{\\\"status\\\":\\\"NORMAL\\\"," 
                + "\\\"address\\\":{\\\"city\\\":\\\"上海\\\"},"
                + "\\\"name\\\":\\\"张三\\\","
                + "\\\"suggestedQuery\\\":[{\\\"query\\\":\\\"上游推荐问\\\","
                + "\\\"intent\\\":null}],"
                + "\\\"linkMapping\\\":[{\\\"linkId\\\":\\\"000001\\\","
                + "\\\"linkContent\\\":\\\"华夏成长\\\"}],"
                + "\\\"cardMapping\\\":[{\\\"cardId\\\":\\\"12343\\\","
                + "\\\"cardName\\\":\\\"哈哈哈\\\","
                + "\\\"cardDescription\\\":\\\"卡片描述\\\","
                + "\\\"htmlContent\\\":\\\"这是html文本\\\","
                + "\\\"cardPlaceHolder\\\":\\\"{card_xxx}\\\"}]}\"}";

        UpstreamStreamFrame frame = converter.convert(raw);

        assertTrue(frame.getMetadata());
        assertEquals(new ObjectMapper().readTree(
                        "{\"body\":{\"status\":\"NORMAL\"," 
                                + "\"address\":{\"city\":\"上海\"},"
                                + "\"name\":\"张三\","
                                + "\"suggestedQuery\":[{\"query\":"
                                + "\"上游推荐问\",\"intent\":null}],"
                                + "\"linkMapping\":[{"
                                + "\"linkId\":\"000001\","
                                + "\"linkContent\":\"华夏成长\"}],"
                                + "\"cardMapping\":[{\"cardId\":\"12343\","
                                + "\"cardName\":\"哈哈哈\","
                                + "\"cardDescription\":\"卡片描述\","
                                + "\"htmlContent\":\"这是html文本\","
                                + "\"cardPlaceHolder\":\"{card_xxx}\"}]},"
                                + "\"returnType\":\"METADATA\"}"),
                new ObjectMapper().readTree(frame.getMetadataJson()));
        assertEquals("华夏成长", frame.getMetadataContent()
                .findLink("000001").get().getLinkContent());
        assertEquals("12343", frame.getMetadataContent()
                .findCard("card_xxx").get().getCardId());
        assertEquals("上游推荐问", frame.getMetadataContent()
                .getSuggestedQuery().get(0).getQuery());
        assertNull(frame.getMetadataContent()
                .getSuggestedQuery().get(0).getIntent());
        assertFalse(frame.hasContent());
        assertFalse(frame.getDone());
    }

    @Test
    void shouldAcceptMetadataContentThatIsAlreadyAnObject() throws Exception {
        String raw = "{\"label\":\"首帧数据\",\"status\":\"METADATA\","
                + "\"content\":{\"status\":\"NORMAL\"," 
                + "\"linkMapping\":[{\"linkId\":\"000503\","
                + "\"linkContent\":\"中信建投景和中短债A\"}],"
                + "\"cardMapping\":[]}}";

        UpstreamStreamFrame frame = converter.convert(raw);

        assertTrue(new ObjectMapper().readTree(frame.getMetadataJson())
                .path("body").isObject());
        assertEquals("中信建投景和中短债A", frame.getMetadataContent()
                .getLinkMapping().get(0).getLinkContent());
        assertFalse(frame.getMetadataContent().requestsFallback());
    }

    @Test
    void shouldExtractOverRiskInfoAsTypedMetadata() {
        String raw = "{\"status\":\"METADATA\",\"content\":{"
                + "\"status\":\"NORMAL\",\"overRiskInfo\":{"
                + "\"userRiskInfo\":{\"userIsMinor\":\"否\","
                + "\"userRiskLevel\":\"A5\","
                + "\"userRiskStatus\":\"已评估，在有效期内\"},"
                + "\"productRiskInfo\":[{\"productName\":\"华夏成长\","
                + "\"productCode\":\"000001\",\"bscRskLvl\":\"R4\"}]}}}";

        UpstreamStreamFrame frame = converter.convert(raw);

        assertEquals("A5", frame.getMetadataContent().getOverRiskInfo()
                .getUserRiskInfo().getUserRiskLevel());
        assertEquals("000001", frame.getMetadataContent().getOverRiskInfo()
                .getProductRiskInfo().get(0).getProductCode());
    }

    @Test
    void shouldExtractThemeRiskTipInfoWithLeadingZeroFundId() {
        String raw = "{\"status\":\"METADATA\",\"content\":{"
                + "\"status\":\"NORMAL\",\"themeRiskTipInfo\":{"
                + "\"userInfo\":{\"riskLevel\":\"C5\","
                + "\"isPrivate\":false,\"growthStyleStatus\":\"OVER\"},"
                + "\"fundList\":[{\"fundId\":\"006281\","
                + "\"riskLevel\":\"R2\",\"fundType\":\"成长\"}]}}}";

        UpstreamStreamFrame frame = converter.convert(raw);

        assertEquals("C5", frame.getMetadataContent().getThemeRiskTipInfo()
                .getUserInfo().getRiskLevel());
        assertEquals(Boolean.FALSE,
                frame.getMetadataContent().getThemeRiskTipInfo()
                        .getUserInfo().getIsPrivate());
        assertEquals("006281", frame.getMetadataContent().getThemeRiskTipInfo()
                .getFundList().get(0).getFundId());
    }

    @Test
    void shouldRejectMalformedOverRiskProductList() {
        String raw = "{\"status\":\"METADATA\",\"content\":{"
                + "\"status\":\"NORMAL\",\"overRiskInfo\":{"
                + "\"productRiskInfo\":{}}}}";

        assertThrows(FilteredStreamException.class,
                () -> converter.convert(raw));
    }

    @Test
    void shouldExtractStructuredFallbackAnswerFromMetadata() {
        String raw = "{\"label\":\"首帧数据\",\"status\":\"METADATA\"," 
                + "\"content\":{\"status\":\"NORMAL\"," 
                + "\"fallbackAnswer\":[{\"type\":\"text\"," 
                + "\"content\":\"上游兜底话术\"," 
                + "\"linkMapping\":[{\"linkId\":\"fallback-link\"," 
                + "\"linkContent\":\"兜底链接\"}],"
                + "\"cardMapping\":[{\"cardId\":\"fallback-card\"," 
                + "\"htmlContent\":\"<div>兜底卡片</div>\"}]},"
                + "{\"type\":\"text\",\"content\":\"第二段兜底话术\"," 
                + "\"linkMapping\":[],\"cardMapping\":[]}]}}";

        UpstreamStreamFrame frame = converter.convert(raw);

        assertEquals("上游兜底话术", frame.getMetadataContent()
                .getFallbackAnswer().get(0).getContent());
        assertEquals("fallback-link", frame.getMetadataContent()
                .getFallbackAnswer().get(0)
                .getLinkMapping().get(0).getLinkId());
        assertEquals("fallback-card", frame.getMetadataContent()
                .getFallbackAnswer().get(0)
                .getCardMapping().get(0).getCardId());
        assertEquals(2, frame.getMetadataContent().getFallbackAnswer().size());
        assertEquals("第二段兜底话术", frame.getMetadataContent()
                .getFallbackAnswer().get(1).getContent());
    }

    @Test
    void shouldIgnoreMalformedFallbackAnswerAndKeepMetadataUsable() {
        String raw = "{\"label\":\"首帧数据\",\"status\":\"METADATA\"," 
                + "\"content\":{\"status\":\"NORMAL\"," 
                + "\"fallbackAnswer\":{\"content\":\"错误结构\"}}}";

        UpstreamStreamFrame frame = converter.convert(raw);

        assertTrue(frame.getMetadata());
        assertTrue(frame.getMetadataContent().getFallbackAnswer().isEmpty());
    }

    @Test
    void shouldReadFallbackInstructionFromActualMetadataContent()
            throws Exception {
        String raw = "{\"label\":\"首帧数据\",\"status\":\"METADATA\"," 
                + "\"content\":\"{\\\"status\\\":\\\"FALLBACK\\\"," 
                + "\\\"reason\\\":\\\"模型要求兜底\\\","
                + "\\\"errorMsg\\\":\\\"上游说明\\\"}\"}";

        UpstreamStreamFrame frame = converter.convert(raw);

        assertTrue(frame.getMetadata());
        assertTrue(frame.getMetadataContent().requestsFallback());
        assertEquals("上游说明", frame.getMetadataContent().getErrorMsg());
        assertEquals("FALLBACK", new ObjectMapper()
                .readTree(frame.getMetadataJson()).path("body")
                .path("status").asText());
        assertEquals("模型要求兜底", new ObjectMapper()
                .readTree(frame.getMetadataJson()).path("body")
                .path("reason").asText());
    }

    @Test
    void shouldReadRefuseInstructionWithoutValidatingUnusedMappings()
            throws Exception {
        String raw = "{\"status\":\"METADATA\",\"content\":\"{"
                + "\\\"status\\\":\\\"REFUSE\\\","
                + "\\\"refuseMsg\\\":\\\"暂不回答\\\","
                + "\\\"fundList\\\":\\\"无须解析\\\"}\"}";

        UpstreamStreamFrame frame = converter.convert(raw);

        assertTrue(frame.getMetadata());
        assertTrue(frame.getMetadataContent().requestsRefuse());
        assertEquals("暂不回答", new ObjectMapper()
                .readTree(frame.getMetadataJson()).path("body")
                .path("refuseMsg").asText());
    }

    @Test
    void shouldRejectDocumentedMetadataFormatsWithoutUpstreamStatus() {
        assertThrows(FilteredStreamException.class,
                () -> converter.convert(
                        "{\"returnType\":\"METADATA\",\"body\":{"
                                + "\"status\":\"FALLBACK\"}}"));
        assertThrows(FilteredStreamException.class,
                () -> converter.convert(
                        "{\"returnType\":\"DATA\",\"body\":{"
                                + "\"answer\":[],\"status\":\"FALLBACK\"}}"));
    }

    @Test
    void shouldRejectMissingMetadataStatus() {
        FilteredStreamException exception = assertThrows(
                FilteredStreamException.class,
                () -> converter.convert(
                        "{\"status\":\"METADATA\","
                                + "\"content\":\"{}\"}"));

        assertTrue(exception.getMessage().contains("Metadata.status"));
    }

    @Test
    void shouldRejectUnknownMetadataStatus() {
        FilteredStreamException exception = assertThrows(
                FilteredStreamException.class,
                () -> converter.convert(
                        "{\"status\":\"METADATA\","
                                + "\"content\":\"{\\\"status\\\":"
                                + "\\\"UNKNOWN\\\"}\"}"));

        assertTrue(exception.getMessage().contains("REFUSE"));
    }

    @Test
    void shouldKeepEveryFieldFromStringMetadataContent() throws Exception {
        String raw = "{\"label\":\"构建源数据\",\"status\":\"METADATA\","
                + "\"content\":\"{\\\"status\\\":\\\"NORMAL\\\"," 
                + "\\\"linkMapping\\\":[{"
                + "\\\"linkId\\\":\\\"000503\\\","
                + "\\\"linkContent\\\":\\\"中信建投景和中短债A\\\","
                + "\\\"riskLevel\\\":\\\"R1\\\"}],"
                + "\\\"cardMapping\\\":[],"
                + "\\\"suggestedQuery\\\":null,"
                + "\\\"agentAnswerSlots\\\":[{\\\"item\\\":\\\"短债\\\","
                + "\\\"type\\\":\\\"基金类型\\\"}],"
                + "\\\"prompt\\\":\\\"# 数据信息；用户指定方向: 短债\\\","
                + "\\\"sections\\\":{\\\"数据收集\\\":\\\"完整章节\\\"},"
                + "\\\"futureField\\\":{\\\"keep\\\":true}}\"}";

        UpstreamStreamFrame frame = converter.convert(raw);
        com.fasterxml.jackson.databind.JsonNode body = new ObjectMapper()
                .readTree(frame.getMetadataJson()).path("body");

        assertTrue(body.isObject());
        assertEquals("R1", body.path("linkMapping").path(0)
                .path("riskLevel").asText());
        assertTrue(body.path("cardMapping").isArray());
        assertTrue(body.path("suggestedQuery").isNull());
        assertEquals("基金类型", body.path("agentAnswerSlots").path(0)
                .path("type").asText());
        assertTrue(body.path("prompt").asText().contains("用户指定方向"));
        assertEquals("完整章节", body.path("sections")
                .path("数据收集").asText());
        // 未来上游新增的字段也不应被白名单裁剪。
        assertTrue(body.path("futureField").path("keep").asBoolean());
    }

    @Test
    void shouldRejectInvalidActualMetadataContent() {
        FilteredStreamException exception = assertThrows(FilteredStreamException.class,
                () -> converter.convert(
                        "{\"status\":\"METADATA\",\"content\":\"not-json\"}"));

        assertTrue(exception.getMessage().contains("content"));
    }

    @Test
    void shouldRejectNonObjectActualMetadataContent() {
        FilteredStreamException exception = assertThrows(FilteredStreamException.class,
                () -> converter.convert(
                        "{\"status\":\"METADATA\",\"content\":\"[1,2]\"}"));

        assertTrue(exception.getMessage().contains("JSON 对象"));
    }

    @Test
    void shouldExtractFundListFromActualMetadataContent() {
        UpstreamStreamFrame frame = converter.convert(
                "{\"status\":\"METADATA\",\"content\":"
                        + "\"{\\\"status\\\":\\\"NORMAL\\\","
                        + "\\\"fundList\\\":{\\\"subtitle\\\":\\\"数据更新日期：2026.8.25\\\","
                        + "\\\"list\\\":[{"
                        + "\\\"fundId\\\":\\\"000001\\\","
                        + "\\\"fundName\\\":\\\"基金一\\\","
                        + "\\\"fundDesc\\\":\\\"基金一描述\\\"}]}}\"}");

        assertTrue(frame.getMetadata());
        assertEquals("数据更新日期：2026.8.25",
                frame.getMetadataContent().getFundList().getSubtitle());
        assertEquals(1, frame.getMetadataContent().getFundList().getList().size());
        assertEquals("000001",
                frame.getMetadataContent().getFundList().getList().get(0).getFundId());
        assertEquals("基金一",
                frame.getMetadataContent().getFundList().getList().get(0).getFundName());
    }

    @Test
    void shouldRejectLegacyFundListArray() {
        FilteredStreamException exception = assertThrows(
                FilteredStreamException.class,
                () -> converter.convert("{\"status\":\"METADATA\","
                        + "\"content\":{\"status\":\"NORMAL\","
                        + "\"fundList\":[{\"fundId\":\"000001\"}]}}"));

        assertTrue(exception.getMessage().contains("fundList 必须是对象"));
    }

    @Test
    void shouldReadOnlyIncrementalContentFromActualMiddleFrame() {
        // 【真实中间帧结构模拟】只读取本帧 content；fullContent 和
        // messageContent 即使存在也不会被重复输出。
        UpstreamStreamFrame frame = converter.convert(
                "{\"content\":\"增量\",\"fullContent\":\"此前增量\","
                        + "\"messageContent\":\"此前增量\",\"status\":\"MIDDLE\"}");

        assertEquals("增量", frame.getContent());
        assertFalse(frame.getDone());
    }

    @Test
    void shouldRejectActualMiddleFrameWithoutTextContent() {
        FilteredStreamException missing = assertThrows(
                FilteredStreamException.class,
                () -> converter.convert("{\"status\":\"MIDDLE\"}"));
        FilteredStreamException nullContent = assertThrows(
                FilteredStreamException.class,
                () -> converter.convert(
                        "{\"status\":\"MIDDLE\",\"content\":null}"));
        FilteredStreamException objectContent = assertThrows(
                FilteredStreamException.class,
                () -> converter.convert(
                        "{\"status\":\"MIDDLE\",\"content\":{}}"));

        assertTrue(missing.getMessage().contains("MIDDLE"));
        assertTrue(nullContent.getMessage().contains("content"));
        assertTrue(objectContent.getMessage().contains("content"));
    }

    @Test
    void shouldDeliverFinalIncrementBeforeCompletingActualStream() {
        // 【真实尾帧结构模拟】END 帧中的 content 仍是最后一段增量，必须先处理，
        // 然后才结束上游流。
        UpstreamStreamFrame frame = converter.convert(
                "{\"content\":\"=\\\"基金名称\\\"/>\","
                        + "\"fullContent\":\"完整回答\",\"status\":\"END\"}");

        assertEquals("=\"基金名称\"/>", frame.getContent());
        assertTrue(frame.hasContent());
        assertTrue(frame.getDone());
        assertTrue(frame.isDeliverable());
    }

    @Test
    void shouldCompleteActualEndFrameWithoutContent() {
        UpstreamStreamFrame frame = converter.convert(
                "{\"status\":\"END\",\"fullContent\":\"完整回答\"}");

        assertNull(frame.getContent());
        assertTrue(frame.getDone());
        assertFalse(frame.isDeliverable());
    }

    @Test
    void shouldRejectOtherDoneMarkers() {
        assertThrows(FilteredStreamException.class,
                () -> converter.convert("[DONE]"));
        assertThrows(FilteredStreamException.class,
                () -> converter.convert("{\"returnType\":\"DONE\"}"));
    }

    @Test
    void shouldRejectNonTextEndContent() {
        FilteredStreamException exception = assertThrows(
                FilteredStreamException.class,
                () -> converter.convert(
                        "{\"status\":\"END\",\"content\":{}}"));

        assertTrue(exception.getMessage().contains("END"));
    }

    @Test
    void shouldTranslateActualUpstreamErrorStatus() {
        // 【真实错误帧结构模拟】ERROR 不向客户端原样透传，而是转换成
        // UpstreamFrameException，由应用服务输出纯 FALLBACK 通知帧。
        FilteredStreamException exception = assertThrows(FilteredStreamException.class,
                () -> converter.convert(
                        "{\"status\":\"ERROR\",\"content\":\"模型连接中断\"}"));

        assertEquals("模型连接中断", exception.getMessage());
    }

}
