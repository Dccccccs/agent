package com.example.agentgateway;

import com.example.agentgateway.application.filteredstream.model.RequestValidationResult;
import com.example.agentgateway.application.filteredstream.model.FilteredStreamRequestContext;
import com.example.agentgateway.application.filteredstream.model.UpstreamStreamFrame;
import com.example.agentgateway.application.filteredstream.UpstreamFrameException;
import com.example.agentgateway.application.filteredstream.port.ExternalModelStreamPort;
import com.example.agentgateway.application.filteredstream.port.FilteredStreamMessageIdGenerator;
import com.example.agentgateway.application.filteredstream.port.FilteredStreamRequestValidator;
import com.example.agentgateway.application.filteredstream.port.FundCardPort;
import com.example.agentgateway.domain.filteredstream.CardDefinition;
import com.example.agentgateway.domain.filteredstream.FallbackMessage;
import com.example.agentgateway.domain.filteredstream.FundCard;
import com.example.agentgateway.domain.filteredstream.FundDefinition;
import com.example.agentgateway.domain.filteredstream.LinkDefinition;
import com.example.agentgateway.domain.filteredstream.MetadataStatus;
import com.example.agentgateway.domain.filteredstream.ProcessedCardMapping;
import com.example.agentgateway.domain.filteredstream.ProcessedLinkMapping;
import com.example.agentgateway.domain.filteredstream.StreamMetadata;
import com.example.agentgateway.domain.filteredstream.SuggestedQueryDefinition;
import com.example.agentgateway.application.dto.AixzChatRequest;
import com.example.agentgateway.interfaces.filteredstream.FilteredModelStreamController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FilteredModelStreamIntegrationTest {
    private static final String ENDPOINT = "/api/v1/model-stream/filtered-query";
    private static final String INTENT_ID = "FUND_RECOMMEND_INTENT";
    private static final String WORKFLOW_ID =
            "filtered-model-stream-workflow-v1";
    private static final String MESSAGE_ID = "message-test-001";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExternalModelStreamPort externalModelClient;

    @MockBean
    private FilteredStreamRequestValidator requestValidator;

    @MockBean
    private FilteredStreamMessageIdGenerator messageIdGenerator;

    @MockBean
    private FundCardPort fundCardPort;

    @BeforeEach
    void stubExternalModelApi() {
        // 【集成测试中的外部 API 模拟入口】
        // 这里 Mock 的是 ExternalModelStreamPort，所以返回的是“已经经过
        // UpstreamStreamFrameConverter 解码”的统一帧，而不是 data: 开头的原始 SSE。
        // 原始厂商 JSON 帧的模拟与解码断言见 UpstreamStreamFrameConverterTest。
        when(requestValidator.validate(any(AixzChatRequest.class)))
                .thenReturn(RequestValidationResult.accepted());
        when(messageIdGenerator.generate()).thenReturn(MESSAGE_ID);
        when(externalModelClient.openStream(
                any(FilteredStreamRequestContext.class)))
                .thenAnswer(invocation -> {
                    FilteredStreamRequestContext requestContext =
                            invocation.getArgument(0);
                    AixzChatRequest request = requestContext.getRequest();
                    String question = request.getQuestion() == null
                            ? "" : request.getQuestion();
                    boolean expectsLink = question.contains(
                            "<url id=\"000001\"");
                    boolean expectsCard = question.contains(
                            "<m-card id='card_xxx'");
                    String metadata = "{\"body\":{\"recordId\":"
                            + "\"upstream-record-id\","
                            + "\"status\":\"NORMAL\"," 
                            + "\"source\":\"external-api\"," 
                            + "\"suggestedQuery\":[{\"query\":\"上游推荐问\"," 
                            + "\"intent\":null}],"
                            + "\"linkMapping\":"
                            + (expectsLink
                            ? "[{\"linkId\":\"000001\","
                                    + "\"linkContent\":\"华夏成长\"}]"
                            : "[]")
                            + ",\"cardMapping\":"
                            + (expectsCard
                            ? "[{\"cardId\":\"12343\","
                                    + "\"cardName\":\"Metadata卡片\","
                                    + "\"cardDescription\":\"卡片描述\","
                                    + "\"htmlContent\":\"这是html文本\","
                                    + "\"cardPlaceHolder\":\"card_xxx\"}]"
                            : "[]")
                            + "},"
                            + "\"returnType\":\"METADATA\"}";
                    CardDefinition card = CardDefinition.builder()
                            .cardPlaceHolder("card_xxx")
                            .cardId("12343")
                            .cardName("Metadata卡片")
                            .cardDescription("卡片描述")
                            .htmlContent("这是html文本")
                            .build();
                    LinkDefinition link = LinkDefinition.builder()
                            .linkId("000001")
                            .linkContent("华夏成长")
                            .build();
                    SuggestedQueryDefinition upstreamSuggestion =
                            SuggestedQueryDefinition.builder()
                                    .query("上游推荐问")
                                    .intent(null)
                                    .build();
                    // Flux.just 按参数顺序模拟外部 API 持续推送：
                    // 第 1 帧 Metadata，随后 3 帧增量正文，最后 Flux 正常完成。
                    return Flux.just(
                            UpstreamStreamFrame.metadata(metadata,
                                    StreamMetadata.builder()
                                            .linkMapping(expectsLink
                                                    ? Collections.singletonList(link)
                                                    : Collections.<LinkDefinition>emptyList())
                                            .cardMapping(expectsCard
                                                    ? Collections.singletonList(card)
                                                    : Collections.<CardDefinition>emptyList())
                                            .suggestedQuery(Collections.singletonList(
                                                    upstreamSuggestion))
                                            .build()),
                            UpstreamStreamFrame.content(
                                    "已收到问题："),
                            UpstreamStreamFrame.content(
                                    request.getQuestion()),
                            UpstreamStreamFrame.content(
                                    "；外部API返回完成。"));
                });
    }

    @Test
    void shouldReturnFallbackAndDoneStreamWhenValidationRefuses() throws Exception {
        when(requestValidator.validate(any(AixzChatRequest.class)))
                .thenReturn(RequestValidationResult.refused("拒绝回答原因"));

        MvcResult result = performAsync(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"refused-1\",\"uid\":\"u-1\","
                        + "\"question\":\"不符合要求的问题\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string(
                        FilteredModelStreamController.MESSAGE_ID_HEADER, ""))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"FALLBACK\"")))
                .andExpect(content().string(containsString(
                        "\"recordId\":\"refused-1\"")))
                .andExpect(content().string(containsString(
                        "抱歉，当前服务暂时不可用，请稍后再试。")))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"DONE\"")))
                .andExpect(content().string(not(containsString("SUC0000"))))
                .andReturn();

        assertFallbackBeforeDone(result);

        verify(externalModelClient, never())
                .openStream(any(FilteredStreamRequestContext.class));
        verify(messageIdGenerator, never()).generate();
    }

    @Test
    void shouldReturnFallbackAndDoneWhenRequiredParameterIsBlank()
            throws Exception {
        MvcResult result = performAsync(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"blank-question\",\"question\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"FALLBACK\"")))
                .andExpect(content().string(containsString(
                        "\"recordId\":\"blank-question\"")))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"DONE\"")))
                .andReturn();

        assertFallbackBeforeDone(result);
        verify(externalModelClient, never())
                .openStream(any(FilteredStreamRequestContext.class));
    }

    @Test
    void shouldReturnFallbackAndDoneWhenRequestJsonIsMalformed()
            throws Exception {
        MvcResult result = mockMvc.perform(post(ENDPOINT)
                .header(FilteredModelStreamController.INTENT_ID_HEADER,
                        INTENT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"broken-json\",\"question\":"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string(
                        FilteredModelStreamController.WORKFLOW_ID_HEADER,
                        WORKFLOW_ID))
                .andExpect(header().string(
                        FilteredModelStreamController.MESSAGE_ID_HEADER, ""))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"FALLBACK\"")))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"DONE\"")))
                .andReturn();

        assertFallbackBeforeDone(result);
        verify(externalModelClient, never())
                .openStream(any(FilteredStreamRequestContext.class));
    }

    @Test
    void shouldReturnFilteredStreamProtocol() throws Exception {
        performAsync(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-client-version", "1.2.0")
                .content("{\"msgId\":\"filtered-1\",\"uid\":\"u-1\","
                        + "\"question\":\"极端高\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string(
                        FilteredModelStreamController.WORKFLOW_ID_HEADER,
                        WORKFLOW_ID))
                .andExpect(header().string(
                        FilteredModelStreamController.MESSAGE_ID_HEADER,
                        MESSAGE_ID))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"METADATA\"")))
                .andExpect(content().string(containsString(
                        "\"recordId\":\"filtered-1\"")))
                .andExpect(content().string(containsString(
                        "\"fallbackAnswer\"")))
                .andExpect(content().string(not(containsString(
                        "upstream-record-id"))))
                .andExpect(content().string(containsString("较高")))
                .andExpect(content().string(not(containsString(
                        "\"data\":"))))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"DONE\"")));

        verify(externalModelClient).openStream(argThat(requestContext ->
                INTENT_ID.equals(requestContext.getIntentId())
                        && "filtered-1".equals(
                                requestContext.getRequest().getMsgId())
                        && MESSAGE_ID.equals(requestContext.getMessageId())
                        && "1.2.0".equals(requestContext.getHeader(
                                "x-client-version"))));
    }

    @Test
    void shouldPreferFallbackAnswerProvidedByUpstreamMetadata()
            throws Exception {
        FallbackMessage upstreamFallback = FallbackMessage.builder()
                .content("上游专属兜底话术")
                .linkMapping(Collections.singletonList(
                        ProcessedLinkMapping.builder()
                                .linkId("upstream-fallback-link")
                                .linkContent("上游兜底链接")
                                .build()))
                .cardMapping(Collections.singletonList(
                        ProcessedCardMapping.builder()
                                .cardId("upstream-fallback-card")
                                .htmlContent("<div>上游兜底卡片</div>")
                                .build()))
                .build();
        FallbackMessage upstreamFallbackSecond = FallbackMessage.builder()
                .content("上游第二段兜底话术")
                .build();
        String metadata = "{\"body\":{\"status\":\"NORMAL\"},"
                + "\"returnType\":\"METADATA\"}";
        when(externalModelClient.openStream(
                any(FilteredStreamRequestContext.class)))
                .thenReturn(Flux.just(
                        UpstreamStreamFrame.metadata(metadata,
                                StreamMetadata.builder()
                                        .fallbackAnswer(Arrays.asList(
                                                upstreamFallback,
                                                upstreamFallbackSecond))
                                        .build()),
                        UpstreamStreamFrame.content("正常正文")));

        performAsync(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"upstream-fallback-answer\","
                        + "\"uid\":\"u-1\",\"question\":\"普通问题\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "上游专属兜底话术")))
                .andExpect(content().string(containsString(
                        "上游第二段兜底话术")))
                .andExpect(content().string(containsString(
                        "upstream-fallback-link")))
                .andExpect(content().string(containsString(
                        "upstream-fallback-card")))
                .andExpect(content().string(not(containsString(
                        "抱歉，当前服务暂时不可用，请稍后再试。"))));
    }

    @Test
    void shouldReturnCardFrameFromCardTag() throws Exception {
        performAsync(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"filtered-card\",\"uid\":\"u-1\","
                        + "\"question\":\"<m-card id='card_xxx' />\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"type\":\"card\"")))
                .andExpect(content().string(containsString("\"cardMapping\"")))
                .andExpect(content().string(containsString("\"cardId\":\"12343\"")))
                .andExpect(content().string(containsString(
                        "card-name=\\\"Metadata卡片\\\"")))
                .andExpect(content().string(containsString("这是html文本")));
    }

    @Test
    void shouldReturnLinkFrameFromFundTag() throws Exception {
        MvcResult result = performAsync(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"filtered-link\",\"uid\":\"u-1\","
                        + "\"question\":\"<url id=\\\"000001\\\" "
                        + "text=\\\"模型输出名称\\\" />\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"linkMapping\"")))
                .andExpect(content().string(containsString(
                        "\"linkId\":\"000001\"")))
                .andExpect(content().string(containsString(
                        "text=\\\"华夏成长\\\"")))
                .andExpect(content().string(not(containsString(
                        "模型输出名称"))))
                .andExpect(content().string(containsString(
                        "\"suggestedQuery\":[{\"query\":\"上游推荐问\"," 
                                + "\"intent\":null},{\"query\":"
                                + "\"深度解读华夏成长\","
                                + "\"intent\":\"null\"}]")))
                .andReturn();

        String stream = result.getResponse().getContentAsString();
        int recommendationIndex = stream.indexOf("深度解读华夏成长");
        int recommendationMetadataIndex = stream.lastIndexOf(
                "\"returnType\":\"METADATA\"");
        int doneIndex = stream.indexOf("\"returnType\":\"DONE\"");
        org.junit.jupiter.api.Assertions.assertTrue(recommendationIndex >= 0);
        org.junit.jupiter.api.Assertions.assertTrue(
                recommendationMetadataIndex > recommendationIndex);
        org.junit.jupiter.api.Assertions.assertTrue(doneIndex > recommendationIndex);
        org.junit.jupiter.api.Assertions.assertTrue(
                doneIndex > recommendationMetadataIndex);
        JsonNode firstBody = firstFrameBody(stream);
        org.junit.jupiter.api.Assertions.assertFalse(
                firstBody.has("linkMapping"));
        org.junit.jupiter.api.Assertions.assertFalse(
                firstBody.has("cardMapping"));
        org.junit.jupiter.api.Assertions.assertFalse(
                firstBody.has("suggestedQuery"));
        org.junit.jupiter.api.Assertions.assertTrue(
                firstBody.path("fallbackAnswer").path(0)
                        .has("linkMapping"));
        org.junit.jupiter.api.Assertions.assertTrue(
                firstBody.path("fallbackAnswer").path(0)
                        .has("cardMapping"));
    }

    @Test
    void shouldReturnFallbackThenDoneForUnknownCardTag()
            throws Exception {
        MvcResult result = performAsync(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"filtered-error\",\"uid\":\"u-1\","
                        + "\"question\":\"<m-card id='card_unknown' />\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "\"returnType\":\"FALLBACK\"")))
                .andExpect(content().string(containsString(
                        "抱歉，当前服务暂时不可用，请稍后再试。")))
                .andExpect(content().string(not(containsString(
                        "\"returnType\":\"ERROR\""))))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"DONE\"")))
                .andReturn();
        assertFallbackBeforeDone(result);
    }

    @Test
    void shouldReturnFallbackWhenUrlIdIsNotInMetadataLinks()
            throws Exception {
        MvcResult result = performAsync(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"unknown-fund\",\"uid\":\"u-1\","
                        + "\"question\":\"<url id=\\\"999999\\\" "
                        + "text=\\\"模型虚构基金\\\" />\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "\"returnType\":\"FALLBACK\"")))
                .andExpect(content().string(containsString(
                        "抱歉，当前服务暂时不可用，请稍后再试。")))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"DONE\"")))
                .andReturn();
        assertFallbackBeforeDone(result);
    }

    @Test
    void shouldReturnFallbackWhenFinalAnswerMissesMetadataMapping()
            throws Exception {
        String metadataJson = "{\"body\":{\"status\":\"NORMAL\","
                + "\"linkMapping\":[{\"linkId\":\"000001\","
                + "\"linkContent\":\"华夏成长\"}]},"
                + "\"returnType\":\"METADATA\"}";
        StreamMetadata metadata = StreamMetadata.builder()
                .link(LinkDefinition.builder()
                        .linkId("000001")
                        .linkContent("华夏成长")
                        .build())
                .status(MetadataStatus.NORMAL)
                .build();
        when(externalModelClient.openStream(
                any(FilteredStreamRequestContext.class)))
                .thenReturn(Flux.just(
                        UpstreamStreamFrame.metadata(metadataJson, metadata),
                        UpstreamStreamFrame.content("正文没有输出声明的基金链接")));

        MvcResult result = performAsync(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"missing-mapping\","
                        + "\"uid\":\"u-1\",\"question\":\"普通问题\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "\"returnType\":\"METADATA\"")))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"DATA\"")))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"FALLBACK\"")))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"DONE\"")))
                .andReturn();

        assertFallbackBeforeDone(result);
    }

    @Test
    void shouldReplaceContinuousFundItemsWithOneGeneratedCard()
            throws Exception {
        String metadataJson = "{\"body\":{\"status\":\"NORMAL\","
                + "\"fundList\":["
                + "{\"fundId\":\"000001\",\"fundName\":\"基金一\","
                + "\"fundDesc\":\"基金一描述\"},"
                + "{\"fundId\":\"000002\",\"fundName\":\"基金二\","
                + "\"fundDesc\":\"基金二描述\"}]},"
                + "\"returnType\":\"METADATA\"}";
        StreamMetadata metadata = StreamMetadata.builder()
                .fund(FundDefinition.builder()
                        .fundId("000001")
                        .fundName("基金一")
                        .fundDesc("基金一描述")
                        .build())
                .fund(FundDefinition.builder()
                        .fundId("000002")
                        .fundName("基金二")
                        .fundDesc("基金二描述")
                        .build())
                .status(MetadataStatus.NORMAL)
                .build();
        when(fundCardPort.createCard(argThat(funds ->
                funds != null && funds.size() == 2
                        && "000001".equals(funds.get(0).getFundId())
                        && "".equals(funds.get(0).getFundDesc())
                        && "000002".equals(funds.get(1).getFundId())
                        && "描述".equals(funds.get(1).getFundDesc()))))
                .thenReturn(FundCard.builder()
                        .cardId("card_combined_001")
                        .htmlContent("<div>组合基金卡片</div>")
                        .build());
        when(externalModelClient.openStream(
                any(FilteredStreamRequestContext.class)))
                .thenReturn(Flux.just(
                        UpstreamStreamFrame.metadata(metadataJson, metadata),
                        UpstreamStreamFrame.content(
                                "前文<li id=\"000001\" text=\""),
                        UpstreamStreamFrame.content(
                                "\" />\n<li id=\"000002\" text=\"描述\" />"),
                        UpstreamStreamFrame.content("后文")));

        MvcResult result = performAsync(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"fund-card-group\","
                        + "\"uid\":\"u-1\",\"question\":\"基金组合\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("前文")))
                .andExpect(content().string(containsString(
                        "<m-card id='card_combined_001' />")))
                .andExpect(content().string(containsString(
                        "\"cardId\":\"card_combined_001\"")))
                .andExpect(content().string(containsString(
                        "<div>组合基金卡片</div>")))
                .andExpect(content().string(containsString("后文")))
                .andExpect(content().string(not(containsString(
                        "\"fundList\""))))
                .andExpect(content().string(not(containsString(
                        "<li id="))))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"DONE\"")))
                .andReturn();

        String stream = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(
                stream.indexOf("前文") < stream.indexOf("card_combined_001"));
        org.junit.jupiter.api.Assertions.assertTrue(
                stream.indexOf("card_combined_001") < stream.indexOf("后文"));
    }

    @Test
    void shouldFallbackWhenFundCardApiFails() throws Exception {
        String metadataJson = "{\"body\":{\"status\":\"NORMAL\","
                + "\"fundList\":[{\"fundId\":\"000001\","
                + "\"fundName\":\"基金一\",\"fundDesc\":\"描述\"}]},"
                + "\"returnType\":\"METADATA\"}";
        StreamMetadata metadata = StreamMetadata.builder()
                .fund(FundDefinition.builder()
                        .fundId("000001")
                        .fundName("基金一")
                        .fundDesc("描述")
                        .build())
                .status(MetadataStatus.NORMAL)
                .build();
        when(fundCardPort.createCard(any()))
                .thenThrow(new IllegalStateException("卡片服务异常"));
        when(externalModelClient.openStream(
                any(FilteredStreamRequestContext.class)))
                .thenReturn(Flux.just(
                        UpstreamStreamFrame.metadata(metadataJson, metadata),
                        UpstreamStreamFrame.content(
                                "<li id=\"000001\" text=\"\" />正文")));

        MvcResult result = performAsync(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"fund-card-error\","
                        + "\"uid\":\"u-1\",\"question\":\"基金组合\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "\"returnType\":\"METADATA\"")))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"FALLBACK\"")))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"DONE\"")))
                .andExpect(content().string(not(containsString(
                        "卡片服务异常"))))
                .andReturn();

        assertFallbackBeforeDone(result);
    }

    @Test
    void shouldPrependFallbackMetadataWhenMetadataValidationFails()
            throws Exception {
        String metadataJson = "{\"body\":{\"status\":\"NORMAL\"},"
                + "\"returnType\":\"METADATA\"}";
        StreamMetadata invalidMetadata = StreamMetadata.builder()
                .link(LinkDefinition.builder()
                        .linkId(null)
                        .linkContent("无效基金")
                        .build())
                .status(MetadataStatus.NORMAL)
                .build();
        when(externalModelClient.openStream(
                any(FilteredStreamRequestContext.class)))
                .thenReturn(Flux.just(UpstreamStreamFrame.metadata(
                        metadataJson, invalidMetadata)));

        MvcResult result = performAsync(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"invalid-metadata\","
                        + "\"question\":\"测试首帧校验失败\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "\"recordId\":\"invalid-metadata\"")))
                .andExpect(content().string(containsString(
                        "抱歉，当前服务暂时不可用，请稍后再试。")))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"FALLBACK\"")))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"DONE\"")))
                .andReturn();

        assertFallbackBeforeDone(result);
    }

    @Test
    void shouldReturnFallbackWhenUpstreamFailsBeforeFirstFrame()
            throws Exception {
        when(externalModelClient.openStream(
                any(FilteredStreamRequestContext.class)))
                .thenReturn(Flux.error(new IllegalStateException(
                        "不应返回给客户端的内部异常")));

        MvcResult result = performAsync(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"upstream-error\",\"uid\":\"u-1\"," 
                        + "\"question\":\"普通问题\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "\"returnType\":\"FALLBACK\"")))
                .andExpect(content().string(containsString(
                        "\"recordId\":\"upstream-error\"")))
                .andExpect(content().string(containsString(
                        "抱歉，当前服务暂时不可用，请稍后再试。")))
                .andExpect(content().string(not(containsString(
                        "不应返回给客户端的内部异常"))))
                .andExpect(content().string(not(containsString(
                        "\"returnType\":\"ERROR\""))))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"DONE\"")))
                .andReturn();
        assertFallbackBeforeDone(result);
    }

    @Test
    void shouldReturnMetadataFallbackAndDoneWhenUpstreamIsEmpty()
            throws Exception {
        when(externalModelClient.openStream(
                any(FilteredStreamRequestContext.class)))
                .thenReturn(Flux.empty());

        MvcResult result = performAsync(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"empty-upstream\",\"uid\":\"u-1\"," 
                        + "\"question\":\"普通问题\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "\"returnType\":\"METADATA\"")))
                .andExpect(content().string(containsString(
                        "\"recordId\":\"empty-upstream\"")))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"FALLBACK\"")))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"DONE\"")))
                .andReturn();

        assertFallbackBeforeDone(result);
    }

    @Test
    void shouldReturnFallbackWhenFirstUpstreamFrameIsError() throws Exception {
        when(externalModelClient.openStream(
                any(FilteredStreamRequestContext.class)))
                .thenReturn(Flux.error(new UpstreamFrameException(
                        "上游 ERROR 帧")));

        MvcResult result = performAsync(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"first-error-frame\",\"uid\":\"u-1\"," 
                        + "\"question\":\"普通问题\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "\"returnType\":\"FALLBACK\"")))
                .andExpect(content().string(not(containsString(
                        "上游 ERROR 帧"))))
                .andReturn();
        assertFallbackBeforeDone(result);
    }

    @Test
    void shouldReturnMetadataThenFallbackWhenMetadataRequestsIt()
            throws Exception {
        AtomicBoolean laterFrameSubscribed = new AtomicBoolean(false);
        String metadata = "{\"body\":{\"recordId\":\"metadata-fallback\"," 
                + "\"status\":\"FALLBACK\"},"
                + "\"returnType\":\"METADATA\"}";
        UpstreamStreamFrame fallbackMetadata = UpstreamStreamFrame.metadata(
                metadata,
                StreamMetadata.builder()
                        .linkMapping(Collections.<LinkDefinition>emptyList())
                        .cardMapping(Collections.<CardDefinition>emptyList())
                        .fallbackAnswer(Collections.singletonList(
                                FallbackMessage.builder()
                                        .content("上游主动兜底话术")
                                        .build()))
                        .status(MetadataStatus.FALLBACK)
                        .build());
        when(externalModelClient.openStream(
                any(FilteredStreamRequestContext.class)))
                .thenReturn(Flux.concat(
                        Flux.just(fallbackMetadata),
                        Flux.defer(() -> {
                            laterFrameSubscribed.set(true);
                            return Flux.just(UpstreamStreamFrame.content(
                                    "不应继续输出的正文"));
                        })));

        MvcResult result = performAsync(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"metadata-fallback\",\"uid\":\"u-1\"," 
                        + "\"question\":\"普通问题\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "\"returnType\":\"METADATA\"")))
                .andExpect(content().string(containsString(
                        "\"status\":\"FALLBACK\"")))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"FALLBACK\"")))
                .andExpect(content().string(containsString(
                        "上游主动兜底话术")))
                .andExpect(content().string(not(containsString(
                        "抱歉，当前服务暂时不可用，请稍后再试。"))))
                .andExpect(content().string(not(containsString(
                        "不应继续输出的正文"))))
                .andReturn();

        String stream = result.getResponse().getContentAsString();
        int metadataIndex = stream.indexOf("\"returnType\":\"METADATA\"");
        int fallbackIndex = stream.indexOf("\"returnType\":\"FALLBACK\"");
        int doneIndex = stream.indexOf("\"returnType\":\"DONE\"");
        org.junit.jupiter.api.Assertions.assertTrue(metadataIndex >= 0);
        org.junit.jupiter.api.Assertions.assertTrue(fallbackIndex > metadataIndex);
        org.junit.jupiter.api.Assertions.assertTrue(doneIndex > fallbackIndex);
        org.junit.jupiter.api.Assertions.assertFalse(laterFrameSubscribed.get());
    }

    @Test
    void shouldReturnFallbackWhenUpstreamFailsAfterMetadata()
            throws Exception {
        String metadata = "{\"body\":{\"recordId\":\"mid-error\"," 
                + "\"status\":\"NORMAL\"},"
                + "\"returnType\":\"METADATA\"}";
        when(externalModelClient.openStream(
                any(FilteredStreamRequestContext.class)))
                .thenReturn(Flux.concat(
                        Flux.just(UpstreamStreamFrame.metadata(metadata)),
                        Flux.error(new IllegalStateException("流中异常"))));

        MvcResult result = performAsync(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"mid-error\",\"uid\":\"u-1\"," 
                        + "\"question\":\"普通问题\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "\"returnType\":\"METADATA\"")))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"FALLBACK\"")))
                .andExpect(content().string(not(containsString("流中异常"))))
                .andReturn();
        assertFallbackBeforeDone(result);
    }

    @Test
    void oldAgentGatewayPathShouldNotExposeNewFeature() throws Exception {
        mockMvc.perform(post("/api/v1/agent/filtered-query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"old-path\",\"question\":\"test\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnFallbackAndWorkflowIdWhenIntentHeaderIsMissing()
            throws Exception {
        MvcResult result = mockMvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"missing-intent\","
                        + "\"question\":\"普通问题\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        FilteredModelStreamController.WORKFLOW_ID_HEADER,
                        WORKFLOW_ID))
                .andExpect(header().string(
                        FilteredModelStreamController.MESSAGE_ID_HEADER, ""))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString(
                        "\"recordId\":\"missing-intent\"")))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"FALLBACK\"")))
                .andExpect(content().string(containsString(
                        "\"returnType\":\"DONE\"")))
                .andReturn();

        assertFallbackBeforeDone(result);
        verify(externalModelClient, never()).openStream(
                any(FilteredStreamRequestContext.class));
        verify(messageIdGenerator, never()).generate();
    }

    private ResultActions performAsync(MockHttpServletRequestBuilder requestBuilder)
            throws Exception {
        requestBuilder.header(
                FilteredModelStreamController.INTENT_ID_HEADER, INTENT_ID);
        MvcResult initial = mockMvc.perform(requestBuilder)
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(initial));
    }

    private void assertFallbackBeforeDone(MvcResult result)
            throws Exception {
        String stream = result.getResponse().getContentAsString();
        int metadataIndex = stream.indexOf(
                "\"returnType\":\"METADATA\"");
        int fallbackIndex = stream.indexOf("\"returnType\":\"FALLBACK\"");
        int doneIndex = stream.indexOf("\"returnType\":\"DONE\"");
        org.junit.jupiter.api.Assertions.assertTrue(metadataIndex >= 0);
        JsonNode firstBody = firstFrameBody(stream);
        org.junit.jupiter.api.Assertions.assertTrue(
                firstBody.has("fallbackAnswer"));
        org.junit.jupiter.api.Assertions.assertTrue(
                firstBody.path("fallbackAnswer").isArray());
        org.junit.jupiter.api.Assertions.assertTrue(
                firstBody.path("fallbackAnswer").path(0).has("content"));
        org.junit.jupiter.api.Assertions.assertTrue(fallbackIndex > metadataIndex);
        org.junit.jupiter.api.Assertions.assertTrue(doneIndex > fallbackIndex);
        int fallbackStart = stream.lastIndexOf("data:", fallbackIndex);
        int fallbackEnd = stream.indexOf("\n\n", fallbackIndex);
        JsonNode fallback = OBJECT_MAPPER.readTree(
                stream.substring(fallbackStart + 5, fallbackEnd));
        org.junit.jupiter.api.Assertions.assertEquals(
                "FALLBACK", fallback.path("returnType").asText());
        org.junit.jupiter.api.Assertions.assertFalse(fallback.has("body"));
        org.junit.jupiter.api.Assertions.assertFalse(fallback.has("message"));
    }

    private JsonNode firstFrameBody(String stream) throws Exception {
        String firstFrame = stream.substring(0, stream.indexOf("\n\n"));
        String json = firstFrame.substring(firstFrame.indexOf("data:") + 5);
        JsonNode payload = OBJECT_MAPPER.readTree(json);
        org.junit.jupiter.api.Assertions.assertEquals(
                "METADATA", payload.path("returnType").asText());
        return payload.path("body");
    }
}
