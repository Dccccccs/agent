package com.example.agentgateway.infrastructure.filteredstream.client;

import com.example.agentgateway.application.filteredstream.FilteredStreamException;
import com.example.agentgateway.application.filteredstream.port.FundCardPort;
import com.example.agentgateway.domain.filteredstream.FundCard;
import com.example.agentgateway.domain.filteredstream.FundCardSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/** 使用临时 JSON 契约同步调用组合基金卡片 API。 */
@Component
@Slf4j
public class RestTemplateFundCardClient implements FundCardPort {
    private final RestTemplate restTemplate;
    private final FundCardApiConverter converter;
    private final String cardApiUrl;

    public RestTemplateFundCardClient(
            RestTemplateBuilder restTemplateBuilder,
            FundCardApiConverter converter,
            @Value("${filtered-stream.card-api.url:"
                    + "http://localhost:18081/api/v1/fund/card}")
            String cardApiUrl,
            @Value("${filtered-stream.card-api.timeout-millis:3000}")
            Long timeoutMillis) {
        long millis = timeoutMillis == null ? 3000L : timeoutMillis.longValue();
        Duration timeout = Duration.ofMillis(millis);
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(timeout)
                .setReadTimeout(timeout)
                .build();
        this.converter = converter;
        this.cardApiUrl = cardApiUrl;
    }

    @Override
    public FundCard createCard(FundCardSource source) {
        try {
            if (source == null || source.getList() == null
                    || source.getList().isEmpty()) {
                throw new IllegalArgumentException("卡片 API 的 fundList.list 不能为空");
            }
            long startedAt = System.currentTimeMillis();
            FundCardApiRequest request = converter.toApiRequest(source);
            FundCardApiResponse response = restTemplate.postForObject(
                    cardApiUrl, request, FundCardApiResponse.class);
            if (response == null) {
                throw new IllegalStateException("卡片 API 返回空响应");
            }
            FundCard card = converter.toDomain(response);
            validateResponse(card);
            log.info("基金卡片 API 调用完成, fundCount={}, elapsedMillis={}",
                    source.getList().size(), System.currentTimeMillis() - startedAt);
            return card;
        } catch (RuntimeException ex) {
            if (ex instanceof FilteredStreamException) {
                throw ex;
            }
            throw new FilteredStreamException("基金卡片 API 调用失败", ex);
        }
    }

    private void validateResponse(FundCard card) {
        if (card == null || isBlank(card.getCardId())
                || isBlank(card.getHtmlContent())) {
            throw new IllegalArgumentException(
                    "卡片 API 返回的 cardId 或 htmlContent 不能为空");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
