package com.example.agentgateway;

import com.example.agentgateway.application.filteredstream.model.FilteredStreamEvent;
import com.example.agentgateway.application.filteredstream.model.RecommendationQuery;
import com.example.agentgateway.domain.filteredstream.FallbackMessage;
import com.example.agentgateway.domain.filteredstream.LinkDefinition;
import com.example.agentgateway.domain.filteredstream.OverRiskInfo;
import com.example.agentgateway.domain.filteredstream.ProcessedCardMapping;
import com.example.agentgateway.domain.filteredstream.ProcessedContentBlock;
import com.example.agentgateway.domain.filteredstream.ProcessedLinkMapping;
import com.example.agentgateway.domain.filteredstream.ProductRiskInfo;
import com.example.agentgateway.domain.filteredstream.UserRiskInfo;
import com.example.agentgateway.interfaces.filteredstream.assembler.FilteredStreamFrameAssembler;
import com.example.agentgateway.interfaces.filteredstream.assembler.FilteredStreamDtoAssembler;
import com.example.agentgateway.interfaces.filteredstream.metadata.MetadataOutputCustomizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilteredStreamFrameAssemblerTest {

    private final FilteredStreamDtoAssembler dtoAssembler =
            Mappers.getMapper(FilteredStreamDtoAssembler.class);
    private final FilteredStreamFrameAssembler assembler =
            new FilteredStreamFrameAssembler(new ObjectMapper(),
                    Collections.<MetadataOutputCustomizer>emptyList(),
                    dtoAssembler);

    @Test
    void shouldMapMetadataToJsonObjectInsteadOfString() {
        Object payload = assembler.toPayload(FilteredStreamEvent.metadata(
                "{\"body\":{\"recordId\":\"r-1\"},"
                        + "\"returnType\":\"METADATA\"}", "request-r-1",
                fallbackMessages()));

        assertTrue(payload instanceof JsonNode);
        JsonNode json = (JsonNode) payload;
        assertTrue(json.isObject());
        assertEquals("METADATA", json.path("returnType").asText());
        assertEquals("request-r-1",
                json.path("body").path("recordId").asText());
    }

    @Test
    void shouldBuildEmptyMetadataWhenUpstreamMetadataIsUnavailable() {
        Object payload = assembler.toPayload(
                FilteredStreamEvent.emptyMetadata(
                        "request-r-2", fallbackMessages()));

        JsonNode json = (JsonNode) payload;
        assertTrue(json.path("body").isObject());
        assertEquals("request-r-2",
                json.path("body").path("recordId").asText());
        assertEquals("METADATA", json.path("returnType").asText());
    }

    @Test
    void shouldRejectNonObjectMetadataPayload() {
        assertThrows(IllegalArgumentException.class,
                () -> assembler.toPayload(FilteredStreamEvent.metadata(
                        "[1,2,3]", "request-r-3", fallbackMessages())));
    }

    @Test
    void shouldCustomizeFinalMetadataBodyWithoutChangingOuterType() {
        MetadataOutputCustomizer customizer = body -> {
            body.remove("internalPrompt");
            body.put("channel", "fund-assistant");
            body.put("fundCount", body.path("linkMapping").size());
        };
        FilteredStreamFrameAssembler customizedAssembler =
                new FilteredStreamFrameAssembler(new ObjectMapper(),
                        Collections.singletonList(customizer), dtoAssembler);

        Object payload = customizedAssembler.toPayload(
                FilteredStreamEvent.metadata(
                        "{\"body\":{\"internalPrompt\":\"不对外输出\"," 
                                + "\"linkMapping\":[{\"fundId\":\"000001\"}]},"
                                + "\"returnType\":\"METADATA\"}",
                        "request-r-4", fallbackMessages()));

        JsonNode json = (JsonNode) payload;
        assertTrue(json.path("body").path("internalPrompt").isMissingNode());
        assertEquals("fund-assistant",
                json.path("body").path("channel").asText());
        assertEquals(1, json.path("body").path("fundCount").asInt());
        assertEquals("METADATA", json.path("returnType").asText());
        assertEquals("request-r-4",
                json.path("body").path("recordId").asText());
    }

    @Test
    void shouldRemoveInternalMappingsAndSuggestedQueryFromFirstMetadataFrame() {
        JsonNode json = (JsonNode) assembler.toPayload(
                FilteredStreamEvent.metadata(
                        "{\"body\":{\"recordId\":\"r-1\"," 
                                + "\"linkMapping\":[{\"linkId\":\"000001\"}],"
                                + "\"cardMapping\":[{\"cardId\":\"card-1\"}],"
                                + "\"suggestedQuery\":[{\"query\":\"旧推荐问\"}],"
                                + "\"overRiskInfo\":{\"productRiskInfo\":[]},"
                                + "\"themeRiskTipInfo\":{\"fundList\":[]}},"
                                + "\"returnType\":\"METADATA\"}",
                        "request-r-5", fallbackMessages()));

        assertFalse(json.path("body").has("linkMapping"));
        assertFalse(json.path("body").has("cardMapping"));
        assertFalse(json.path("body").has("suggestedQuery"));
        assertFalse(json.path("body").has("overRiskInfo"));
        assertFalse(json.path("body").has("themeRiskTipInfo"));
        assertEquals("request-r-5",
                json.path("body").path("recordId").asText());
        JsonNode fallbackAnswer = json.path("body").path("fallbackAnswer")
                .path(0);
        assertEquals("text", fallbackAnswer.path("type").asText());
        assertEquals("兜底话术", fallbackAnswer.path("content").asText());
        assertEquals("fallback-link", fallbackAnswer.path("linkMapping")
                .path(0).path("linkId").asText());
        assertEquals("fallback-card", fallbackAnswer.path("cardMapping")
                .path(0).path("cardId").asText());
    }

    @Test
    void shouldReturnRecommendationAsMetadataFrame() {
        JsonNode json = new ObjectMapper().valueToTree(
                assembler.toPayload(FilteredStreamEvent.recommendations(
                        Collections.singletonList(new RecommendationQuery(
                                "深度解读华夏成长", "null")))));

        assertEquals("METADATA", json.path("returnType").asText());
        assertEquals("深度解读华夏成长", json.path("body")
                .path("suggestedQuery").path(0).path("query").asText());
        assertFalse(json.path("body").has("operate"));
    }

    @Test
    void shouldKeepRiskInfoStructureAndOnlyOutputRecommendedProducts() {
        OverRiskInfo matchedRiskInfo = OverRiskInfo.builder()
                .userRiskInfo(UserRiskInfo.builder()
                        .userIsMinor("否")
                        .userRiskLevel("A5")
                        .userRiskStatus("已评估")
                        .build())
                .productRiskInfo(Collections.singletonList(
                        ProductRiskInfo.builder()
                                .productName("华夏成长")
                                .productCode("000001")
                                .bscRskLvl("R4")
                                .build()))
                .build();
        JsonNode json = new ObjectMapper().valueToTree(assembler.toPayload(
                FilteredStreamEvent.recommendations(
                        Collections.singletonList(new RecommendationQuery(
                                "深度解读华夏成长", null)),
                        matchedRiskInfo)));

        JsonNode body = json.path("body");
        assertEquals("METADATA", json.path("returnType").asText());
        assertEquals("深度解读华夏成长",
                body.path("suggestedQuery").path(0).path("query").asText());
        assertEquals("A5", body.path("overRiskInfo")
                .path("userRiskInfo").path("userRiskLevel").asText());
        assertEquals(1, body.path("overRiskInfo")
                .path("productRiskInfo").size());
        assertEquals("000001", body.path("overRiskInfo")
                .path("productRiskInfo").path(0).path("productCode").asText());
    }

    @Test
    void shouldFlattenContentAndMappingsIntoAnswerBlock() {
        ProcessedContentBlock block = ProcessedContentBlock.link(
                "<url id=\"000001\" text=\"华夏成长\"/>",
                LinkDefinition.builder()
                        .linkId("000001")
                        .linkContent("华夏成长")
                        .build());

        JsonNode json = new ObjectMapper().valueToTree(
                assembler.toPayload(FilteredStreamEvent.content(block)));
        JsonNode answer = json.path("body").path("answer").path(0);

        assertEquals("text", answer.path("type").asText());
        assertEquals(block.getContent(), answer.path("content").asText());
        assertEquals("000001", answer.path("linkMapping").path(0)
                .path("linkId").asText());
        assertFalse(answer.has("data"));
    }

    @Test
    void shouldReturnFallbackTypeWithoutDuplicatingAnswer() {
        JsonNode json = new ObjectMapper().valueToTree(
                assembler.toPayload(FilteredStreamEvent.fallback()));

        assertEquals("FALLBACK", json.path("returnType").asText());
        assertFalse(json.has("body"));
        assertFalse(json.has("message"));
    }

    private List<FallbackMessage> fallbackMessages() {
        return Collections.singletonList(FallbackMessage.builder()
                .content("兜底话术")
                .linkMapping(Collections.singletonList(
                        ProcessedLinkMapping.builder()
                                .linkId("fallback-link")
                                .linkContent("[帮助](https://example.com)")
                                .build()))
                .cardMapping(Collections.singletonList(
                        ProcessedCardMapping.builder()
                                .cardId("fallback-card")
                                .htmlContent("<div>兜底卡片</div>")
                                .build()))
                .build());
    }
}
