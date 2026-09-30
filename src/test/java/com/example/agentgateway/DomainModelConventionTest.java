package com.example.agentgateway;

import com.example.agentgateway.domain.agent.result.OrderQueryAgentResult;
import com.example.agentgateway.domain.agent.result.WeatherAgentResult;
import com.example.agentgateway.domain.model.AgentInfo;
import com.example.agentgateway.domain.model.GatewayContext;
import com.example.agentgateway.domain.model.IntentRecognitionResult;
import com.example.agentgateway.domain.model.AgentGatewayRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class DomainModelConventionTest {
    @Test
    void domainEntitiesShouldUseLombokBuilderWithoutManualConstructors()
            throws IOException {
        Path domainRoot = Paths.get("src", "main", "java", "com", "example",
                "agentgateway", "domain");
        List<Path> entitySources;
        try (Stream<Path> paths = Files.walk(domainRoot)) {
            entitySources = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(this::isDomainEntitySource)
                    .collect(Collectors.toList());
        }

        assertFalse(entitySources.isEmpty(), "未发现需要校验的领域实体");
        for (Path sourceFile : entitySources) {
            String source = readSource(sourceFile);
            assertTrue(source.contains("@Builder"),
                    sourceFile + " 必须在类上使用Lombok @Builder");

            String typeName = sourceFile.getFileName().toString()
                    .replace(".java", "");
            Pattern manualConstructor = Pattern.compile(
                    "(?m)^\\s*(?:public|protected|private)\\s+"
                            + Pattern.quote(typeName) + "\\s*\\(");
            assertFalse(manualConstructor.matcher(source).find(),
                    sourceFile + " 不允许手写构造器，请通过@Builder创建实体");
        }
    }

    @Test
    void domainModelsShouldNotExposeSetterMethods() {
        List<Class<?>> domainModels = Arrays.<Class<?>>asList(
                AgentInfo.class, GatewayContext.class,
                IntentRecognitionResult.class);
        for (Class<?> model : domainModels) {
            for (Method method : model.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers()) && method.getName().matches("set[A-Z].*")) {
                    fail(model.getSimpleName() + " 不允许暴露Setter方法: " + method.getName());
                }
            }
        }
    }

    @Test
    void shouldRecordIntentResultAndRouteAgent() {
        AgentGatewayRequest request = AgentGatewayRequest.builder()
                .msgId("r-1")
                .uid("u-1")
                .question("天气如何")
                .build();
        GatewayContext context = GatewayContext.builder().request(request).build();
        IntentRecognitionResult recognitionResult = IntentRecognitionResult.builder()
                .intentId("WEATHER_INTENT")
                .intentResult(Collections.<String, Object>singletonMap("confidence", 0.95D))
                .build();
        Instant requestTime = Instant.parse("2026-08-07T01:00:00Z");
        Instant responseTime = Instant.parse("2026-08-07T01:00:01Z");

        context.beginIntentRecognition(requestTime);
        context.completeIntentRecognition(recognitionResult, responseTime, 1000L);
        context.routeToAgent("weather-agent");

        assertEquals("WEATHER_INTENT", context.getIntentId());
        assertEquals(0.95D, context.getIntentResult().get("confidence"));
        assertEquals("weather-agent", context.getAgentId());
        assertEquals(requestTime, context.getIntentRequestTime());
        assertEquals(responseTime, context.getIntentResponseTime());
        assertEquals(1000L, context.getIntentRecognitionMillis());
    }

    @Test
    void shouldSelectResultIdByUseOffline() {
        AgentGatewayRequest request = AgentGatewayRequest.builder()
                .msgId("r-result")
                .question("result test")
                .build();

        GatewayContext onlineContext = GatewayContext.builder().request(request).build();
        onlineContext.completeAgentExecution(
                AgentInfo.builder().agentCode("online-agent").useOffline(0).build(),
                WeatherAgentResult.builder().workflowId("workflow-001").build());
        assertEquals("workflow-001", onlineContext.getAgentResultId());

        GatewayContext offlineContext = GatewayContext.builder().request(request).build();
        offlineContext.completeAgentExecution(
                AgentInfo.builder().agentCode("offline-agent").useOffline(1).build(),
                OrderQueryAgentResult.builder().recordId("record-001").build());
        assertEquals("record-001", offlineContext.getAgentResultId());

        GatewayContext invalidOnlineContext = GatewayContext.builder().request(request).build();
        assertThrows(IllegalArgumentException.class,
                () -> invalidOnlineContext.completeAgentExecution(
                        AgentInfo.builder().agentCode("invalid-online-agent").useOffline(0).build(),
                        WeatherAgentResult.builder().build()));
    }

    private boolean isDomainEntitySource(Path sourceFile) {
        try {
            String source = readSource(sourceFile);
            return source.contains("@Getter")
                    && !Pattern.compile("\\benum\\s+\\w+").matcher(source).find()
                    && !Pattern.compile("\\bextends\\s+(?:RuntimeException|Exception)\\b")
                    .matcher(source).find();
        } catch (IOException exception) {
            throw new IllegalStateException("读取领域源码失败: " + sourceFile,
                    exception);
        }
    }

    private String readSource(Path sourceFile) throws IOException {
        return new String(Files.readAllBytes(sourceFile),
                StandardCharsets.UTF_8);
    }
}
