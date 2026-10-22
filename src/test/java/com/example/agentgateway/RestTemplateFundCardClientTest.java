package com.example.agentgateway;

import com.example.agentgateway.domain.filteredstream.FundCard;
import com.example.agentgateway.domain.filteredstream.FundCardSource;
import com.example.agentgateway.domain.filteredstream.FundDefinition;
import com.example.agentgateway.infrastructure.filteredstream.client.FundCardApiConverter;
import com.example.agentgateway.infrastructure.filteredstream.client.RestTemplateFundCardClient;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestTemplateFundCardClientTest {
    @Test
    void shouldPostFundListAndReturnOneCard() {
        String url = "http://localhost:18081/api/v1/fund/card";
        FundCardApiConverter converter = Mappers.getMapper(
                FundCardApiConverter.class);
        RestTemplateFundCardClient client = new RestTemplateFundCardClient(
                new RestTemplateBuilder(), converter, url, 3000L);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(
                client, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer
                .createServer(restTemplate);
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"fundList\":{\"subtitle\":\"数据更新日期：2026.8.25\","
                        + "\"list\":[{\"fundId\":\"000001\","
                        + "\"fundName\":\"基金一\",\"fundDesc\":\"模型描述\"}]}}"))
                .andRespond(withSuccess("{\"cardId\":\"card_001\","
                        + "\"htmlContent\":\"<div>卡片</div>\"}",
                        MediaType.APPLICATION_JSON));

        FundCard card = client.createCard(FundCardSource.builder()
                .subtitle("数据更新日期：2026.8.25")
                .list(Collections.singletonList(FundDefinition.builder()
                        .fundId("000001")
                        .fundName("基金一")
                        .fundDesc("模型描述")
                        .build()))
                .build());

        assertEquals("card_001", card.getCardId());
        assertEquals("<div>卡片</div>", card.getHtmlContent());
        server.verify();
    }
}
