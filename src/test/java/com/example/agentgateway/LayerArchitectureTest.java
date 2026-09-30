package com.example.agentgateway;

import com.example.agentgateway.application.response.FallbackAgentResponse;
import com.example.agentgateway.application.response.OrderQueryAgentResponse;
import com.example.agentgateway.application.response.WeatherAgentResponse;
import com.example.agentgateway.application.filteredstream.model.UpstreamStreamFrame;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LayerArchitectureTest {
    private static final Pattern NESTED_TYPE_DECLARATION = Pattern.compile(
            "(?m)^[ \\t]+(?:(?:public|protected|private|static|final|abstract)\\s+)*"
                    + "(?:class|interface|enum)\\s+");
    private static final Pattern ANONYMOUS_TYPE_DECLARATION = Pattern.compile(
            "new\\s+[\\w.$<>?,\\s]+\\([^;{}]*\\)\\s*\\{");

    @Test
    void applicationResponsesShouldNotExposeDomainTypes() {
        List<Class<?>> responseTypes = Arrays.<Class<?>>asList(
                WeatherAgentResponse.class,
                OrderQueryAgentResponse.class,
                FallbackAgentResponse.class);

        for (Class<?> responseType : responseTypes) {
            for (Field field : responseType.getDeclaredFields()) {
                assertFalse(field.getType().getName().startsWith(
                                "com.example.agentgateway.domain"),
                        responseType.getSimpleName() + "." + field.getName()
                                + " 不允许直接暴露Domain类型");
            }
        }
    }

    @Test
    void sourceRootShouldContainOnlyFourArchitectureLayers()
            throws IOException {
        Path sourceRoot = sourceRoot();
        Set<String> actualLayers;
        try (Stream<Path> paths = Files.list(sourceRoot)) {
            actualLayers = paths.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }
        assertEquals(new HashSet<String>(Arrays.asList(
                "application", "domain", "infrastructure", "interfaces")),
                actualLayers);
    }

    @Test
    void allFeaturesShouldFollowFourLayerDependencyDirection()
            throws IOException {
        Path sourceRoot = sourceRoot();
        assertNoForbiddenImports(sourceRoot.resolve("domain"),
                "com.example.agentgateway.application",
                "com.example.agentgateway.interfaces",
                "com.example.agentgateway.infrastructure",
                "org.springframework");
        assertNoForbiddenImports(sourceRoot.resolve("application"),
                "com.example.agentgateway.interfaces",
                "com.example.agentgateway.infrastructure");
        assertNoForbiddenImports(sourceRoot.resolve("interfaces"),
                "com.example.agentgateway.infrastructure");
        assertNoForbiddenImports(sourceRoot.resolve("infrastructure"),
                "com.example.agentgateway.interfaces");
    }

    @Test
    void applicationObjectTransformersShouldUseAssemblerNaming()
            throws IOException {
        Path applicationRoot = sourceRoot().resolve("application");
        try (Stream<Path> paths = Files.walk(applicationRoot)) {
            List<String> invalidNames = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith("Mapper.java")
                            || name.endsWith("Converter.java"))
                    .collect(Collectors.toList());
            assertEquals(Arrays.<String>asList(), invalidNames,
                    "Application层对象转换器必须使用Assembler命名");
        }
    }

    @Test
    void loggingShouldUseLombokSlf4jAnnotation() throws IOException {
        assertNoForbiddenImports(sourceRoot(),
                "org.slf4j.Logger;", "org.slf4j.LoggerFactory");
    }

    @Test
    void productionCodeShouldNotUseBuilderDefault() throws IOException {
        assertNoForbiddenImports(sourceRoot(), "@Builder.Default");
    }

    @Test
    void dtoFieldsShouldUseReferenceTypes() throws Exception {
        try (Stream<Path> paths = Files.walk(sourceRoot())) {
            List<Path> dtoSources = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> sourceRoot().relativize(path).toString()
                            .replace('\\', '/').contains("/dto/"))
                    .collect(Collectors.toList());
            for (Path sourceFile : dtoSources) {
                String relativeName = sourceRoot().relativize(sourceFile)
                        .toString().replace('\\', '.').replace('/', '.');
                String className = "com.example.agentgateway."
                        + relativeName.substring(0,
                        relativeName.length() - ".java".length());
                Class<?> dtoType = Class.forName(className, false,
                        getClass().getClassLoader());
                for (Field field : dtoType.getDeclaredFields()) {
                    assertFalse(field.getType().isPrimitive(),
                            dtoType.getSimpleName() + "." + field.getName()
                                    + " 必须使用包装类型，不能使用基本类型");
                }
            }
        }
    }

    @Test
    void upstreamStreamFrameFieldsShouldUseReferenceTypes() {
        for (Field field : UpstreamStreamFrame.class.getDeclaredFields()) {
            assertFalse(field.getType().isPrimitive(),
                    "UpstreamStreamFrame." + field.getName()
                            + " 必须使用包装类型，不能使用基本类型");
        }
    }

    @Test
    void productionCodeShouldPreferIndependentTopLevelTypes()
            throws IOException {
        try (Stream<Path> paths = Files.walk(sourceRoot())) {
            List<Path> sourceFiles = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
            for (Path sourceFile : sourceFiles) {
                String source = new String(Files.readAllBytes(sourceFile),
                        StandardCharsets.UTF_8);
                assertFalse(NESTED_TYPE_DECLARATION.matcher(source).find(),
                        sourceFile + " 不应声明成员类、局部类或嵌套枚举");
                assertFalse(ANONYMOUS_TYPE_DECLARATION.matcher(source).find(),
                        sourceFile + " 不应声明匿名类");
            }
        }
    }

    private Path sourceRoot() {
        return Paths.get("src", "main", "java", "com", "example",
                "agentgateway");
    }

    private void assertNoForbiddenImports(Path layerRoot, String... forbidden)
            throws IOException {
        List<Path> sourceFiles;
        try (Stream<Path> paths = Files.walk(layerRoot)) {
            sourceFiles = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
        for (Path sourceFile : sourceFiles) {
            String source = new String(Files.readAllBytes(sourceFile),
                    StandardCharsets.UTF_8);
            for (String forbiddenText : forbidden) {
                assertFalse(source.contains(forbiddenText),
                        sourceFile + " 不允许依赖 " + forbiddenText);
            }
        }
    }
}
