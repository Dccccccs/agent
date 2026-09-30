package com.example.agentgateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
class AgentGatewayIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldConvertWeatherAgentResult() throws Exception {
        mockMvc.perform(post("/api/v1/agent/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"r-1\",\"sessionId\":\"s-1\",\"uid\":\"u-1\",\"appId\":\"app-1\",\"question\":\"今天北京天气如何\",\"rewriteQuestion\":\"北京天气\",\"slot\":{\"MutualFund\":[{\"start\":0,\"end\":4,\"key\":\"测试基金\",\"matchedInfo\":{\"origin_name\":\"测试基金\",\"code\":\"000001\",\"fund_type\":\"混合型\"}}]},\"extraParam\":{\"channel\":\"app\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowId").value("weather-workflow-r-1"))
                .andExpect(jsonPath("$.recordId").doesNotExist())
                .andExpect(jsonPath("$.msgId").doesNotExist())
                .andExpect(jsonPath("$.intentId").doesNotExist())
                .andExpect(jsonPath("$.agentId").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldConvertOrderAgentResult() throws Exception {
        mockMvc.perform(post("/api/v1/agent/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"r-3\",\"uid\":\"u-3\",\"question\":\"查询我的订单\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordId").value("order-record-r-3"))
                .andExpect(jsonPath("$.uid").value("u-3"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldConvertFallbackAgentResult() throws Exception {
        mockMvc.perform(post("/api/v1/agent/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"r-5\",\"uid\":\"u-5\",\"question\":\"其他问题\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordId").value("fallback-record-r-5"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturnSseForStreamingAgent() throws Exception {
        performAsync(post("/api/v1/agent/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"r-stream\",\"uid\":\"u-stream\",\"question\":\"stream answer\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:message")))
                .andExpect(content().string(containsString("event:complete")))
                .andExpect(content().string(containsString("stream-workflow-r-stream")));
    }

    private ResultActions performAsync(MockHttpServletRequestBuilder requestBuilder)
            throws Exception {
        MvcResult initial = mockMvc.perform(requestBuilder)
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(initial));
    }

    @Test
    void shouldRejectUnsafeQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/agent/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"r-2\",\"uid\":\"u-1\",\"question\":\"违禁内容\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUESTION_REJECTED"));
    }

    @Test
    void shouldTranslateIntentServiceException() throws Exception {
        mockMvc.perform(post("/api/v1/agent/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"msgId\":\"r-4\",\"uid\":\"u-4\",\"question\":\"模拟意图异常\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INTENT_RECOGNITION_FAILED"))
                .andExpect(jsonPath("$.message").value("意图识别服务调用失败"));
    }
}
