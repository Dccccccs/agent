package com.example.agentgateway;

import com.example.agentgateway.application.filteredstream.FilteredStreamException;
import com.example.agentgateway.application.filteredstream.model.UpstreamStreamFrame;
import com.example.agentgateway.infrastructure.filteredstream.client.UpstreamStreamFrameConverter;
import com.fasterxml.jackson.databind.JsonNode;
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
    void shouldDecodePlainSseData() {
        // 【原始外部 SSE 帧模拟】生产环境中 data: 前缀通常由 WebClient 的
        // ServerSentEvent 解包掉；解码器仍兼容直接传入完整 data 行。
        UpstreamStreamFrame frame =
                converter.convert("data: 普通增量文本");

        assertEquals("普通增量文本", frame.getContent());
        assertFalse(frame.getDone());
    }

    @Test
    void shouldDecodeOpenAiCompatibleDelta() {
        UpstreamStreamFrame frame = converter.convert(
                "{\"choices\":[{\"delta\":{\"content\":\"增量\"}}]}");

        assertEquals("增量", frame.getContent());
    }

    @Test
    void shouldDecodeDocumentedDataFrame() {
        UpstreamStreamFrame frame = converter.convert(
                "{\"returnType\":\"DATA\",\"body\":{\"answer\":[{"
                        + "\"type\":\"text\",\"data\":{\"content\":\"正文\"}}]}}");

        assertEquals("正文", frame.getContent());
    }

    @Test
    void shouldNormalizeDocumentedMetadataToTargetObjectShape()
            throws Exception {
        String raw = "{\"body\":{\"answer\":[],\"status\":\"NORMAL\"," 
                + "\"sections\":{\"fund\":\"x\"},"
                + "\"recordId\":\"r-1\"},\"returnType\":\"DATA\","
                + "\"customField\":\"keep\"}";

        UpstreamStreamFrame frame = converter.convert(raw);

        assertTrue(frame.getMetadata());
        JsonNode metadata = new ObjectMapper().readTree(
                frame.getMetadataJson());
        assertTrue(metadata.isObject());
        assertEquals("METADATA", metadata.path("returnType").asText());
        assertEquals("r-1", metadata.path("body").path("recordId").asText());
        // 外部包装层字段不属于最终首帧协议，不继续向下游透传。
        assertFalse(metadata.has("customField"));
        assertFalse(frame.hasContent());
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
                + "\\\"reason\\\":\\\"模型要求兜底\\\"}\"}";

        UpstreamStreamFrame frame = converter.convert(raw);

        assertTrue(frame.getMetadata());
        assertTrue(frame.getMetadataContent().requestsFallback());
        assertEquals("FALLBACK", new ObjectMapper()
                .readTree(frame.getMetadataJson()).path("body")
                .path("status").asText());
        assertEquals("模型要求兜底", new ObjectMapper()
                .readTree(frame.getMetadataJson()).path("body")
                .path("reason").asText());
    }

    @Test
    void shouldReadFallbackInstructionFromDocumentedMetadataFrame() {
        UpstreamStreamFrame frame = converter.convert(
                "{\"returnType\":\"METADATA\",\"body\":{"
                        + "\"status\":\"FALLBACK\"}}");

        assertTrue(frame.getMetadata());
        assertTrue(frame.getMetadataContent().requestsFallback());
    }

    @Test
    void shouldRecognizeFallbackOnlyFirstDataFrameAsMetadata() {
        UpstreamStreamFrame frame = converter.convert(
                "{\"returnType\":\"DATA\",\"body\":{"
                        + "\"answer\":[],\"status\":\"FALLBACK\"}}");

        assertTrue(frame.getMetadata());
        assertTrue(frame.getMetadataContent().requestsFallback());
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

        assertTrue(exception.getMessage().contains("NORMAL 或 FALLBACK"));
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
    void shouldRecognizeDoneMarkers() {
        assertTrue(converter.convert("[DONE]").getDone());
        assertTrue(converter.convert("{\"returnType\":\"DONE\"}").getDone());
    }

    @Test
    void shouldTranslateUpstreamErrorFrame() {
        FilteredStreamException exception = assertThrows(FilteredStreamException.class,
                () -> converter.convert(
                        "{\"returnType\":\"ERROR\",\"message\":\"模型异常\"}"));

        assertEquals("模型异常", exception.getMessage());
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
