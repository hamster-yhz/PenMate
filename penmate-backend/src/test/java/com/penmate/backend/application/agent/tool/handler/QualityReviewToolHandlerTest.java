package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QualityReviewToolHandlerTest {

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_TOOL_HANDLER_EXECUTE_SHOULD_DELEGATE_TO_APPLICATION_SERVICE() throws Exception {
        Class<?> serviceType = loadClass("com.penmate.backend.application.agent.tool.QualityReviewApplicationService");
        ToolCallResult delegatedResult = ToolCallResult.success("{\"delegated\":true}");
        Object service = Proxy.newProxyInstance(
                serviceType.getClassLoader(),
                new Class<?>[]{serviceType},
                (proxy, method, args) -> {
                    if ("review".equals(method.getName())) {
                        assertThat(args).hasSize(1);
                        assertThat(args[0]).isInstanceOf(ToolCallRequest.class);
                        return delegatedResult;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
        Object handler = instantiateQualityReviewToolHandler(service);

        ToolCallResult result = execute(handler, request("call-quality-delegate", validArgsJson()));

        assertThat(result).isSameAs(delegatedResult);
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_TOOL_HANDLER_EXECUTE_SHOULD_RETURN_STABLE_FAILED_RESULT_WHEN_REQUEST_IS_NULL() throws Exception {
        Class<?> serviceType = loadClass("com.penmate.backend.application.agent.tool.QualityReviewApplicationService");
        Object service = Proxy.newProxyInstance(
                serviceType.getClassLoader(),
                new Class<?>[]{serviceType},
                (proxy, method, args) -> {
                    throw new AssertionError("service should not be called");
                }
        );
        Object handler = instantiateQualityReviewToolHandler(service);

        ToolCallResult result = execute(handler, null);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("QUALITY_REVIEW_FAILED");
        assertThat(result.errorMessage()).isEqualTo("request must not be null");
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_TOOL_HANDLER_EXECUTE_SHOULD_MAP_APPLICATION_SERVICE_ARGUMENT_ERROR_TO_STABLE_FAILED_RESULT() throws Exception {
        Class<?> serviceType = loadClass("com.penmate.backend.application.agent.tool.QualityReviewApplicationService");
        Object service = Proxy.newProxyInstance(
                serviceType.getClassLoader(),
                new Class<?>[]{serviceType},
                (proxy, method, args) -> {
                    if ("review".equals(method.getName())) {
                        throw new IllegalArgumentException("toolArgsJson must be valid JSON");
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
        Object handler = instantiateQualityReviewToolHandler(service);

        ToolCallResult result = execute(handler, request("call-quality-invalid-delegation", "{"));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("QUALITY_REVIEW_FAILED");
        assertThat(result.errorMessage()).contains("toolArgsJson must be valid JSON");
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_TOOL_HANDLER_EXECUTE_SHOULD_MAP_APPLICATION_SERVICE_RUNTIME_ERROR_TO_STABLE_FAILED_RESULT() throws Exception {
        Class<?> serviceType = loadClass("com.penmate.backend.application.agent.tool.QualityReviewApplicationService");
        Object service = Proxy.newProxyInstance(
                serviceType.getClassLoader(),
                new Class<?>[]{serviceType},
                (proxy, method, args) -> {
                    if ("review".equals(method.getName())) {
                        throw new IllegalStateException("provider timeout");
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
        Object handler = instantiateQualityReviewToolHandler(service);

        ToolCallResult result = execute(handler, request("call-quality-runtime-error", validArgsJson()));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("QUALITY_REVIEW_FAILED");
        assertThat(result.errorMessage()).isEqualTo("provider timeout");
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_TOOL_DEFINITION_SHOULD_EXPOSE_SCHEMA_DISPLAY_NAME_AND_GOVERNANCE_POLICY() throws Exception {
        Object definition = instantiateQualityReviewToolDefinition();
        Object descriptor = definition.getClass().getMethod("descriptor").invoke(definition);

        assertThat(readAccessor(descriptor, "toolCode")).isEqualTo("quality_review");

        Object presentation = readAccessor(descriptor, "presentation");
        assertThat(readAccessor(presentation, "displayName")).isEqualTo("质量审查");

        Object exposure = readAccessor(descriptor, "exposure");
        assertThat(readAccessor(exposure, "exposedToLlm")).isEqualTo(true);
        String schema = String.valueOf(readAccessor(exposure, "parametersJsonSchema"));
        assertThat(schema)
                .contains("\"chapterId\"")
                .contains("\"draftId\"")
                .contains("\"userRequirements\"")
                .contains("\"personaProfile\"")
                .contains("\"storyOutline\"")
                .contains("\"timelineConstraints\"")
                .contains("\"worldRules\"")
                .contains("\"characterKnowledgeBoundaries\"")
                .contains("\"currentRevisionRound\"")
                .contains("\"maxRevisionRounds\"")
                .contains("must be less than or equal to maxRevisionRounds")
                .doesNotContain("\"required\": [\"draftText\"")
                .contains("\"additionalProperties\": false");

        Object governancePolicy = readAccessor(descriptor, "governancePolicy");
        assertThat(readAccessor(governancePolicy, "riskLevel")).isEqualTo(1);
        Object defaultDecision = readAccessor(governancePolicy, "defaultDecision");
        assertThat(readAccessor(defaultDecision, "approvalRequired")).isEqualTo(false);
        Map<?, ?> operationPolicies = castMap(readAccessor(governancePolicy, "operationPolicies"));
        assertThat(operationPolicies).isEmpty();
    }

    @Test
    void UT_APP_AGENT_QUALITY_REVIEW_TOOL_HANDLER_VALIDATE_SHOULD_ONLY_REJECT_NULL_REQUEST() throws Exception {
        Class<?> serviceType = loadClass("com.penmate.backend.application.agent.tool.QualityReviewApplicationService");
        Object service = Proxy.newProxyInstance(
                serviceType.getClassLoader(),
                new Class<?>[]{serviceType},
                (proxy, method, args) -> ToolCallResult.success("{}")
        );
        Object handler = instantiateQualityReviewToolHandler(service);
        Method validateMethod = handler.getClass().getMethod("validate", ToolCallRequest.class);

        validateMethod.invoke(handler, request("call-quality-invalid", "{"));

        assertThatThrownBy(() -> {
            try {
                validateMethod.invoke(handler, new Object[]{null});
            } catch (InvocationTargetException ex) {
                throw ex.getTargetException();
            }
        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request must not be null");
    }

    private static Object instantiateQualityReviewToolDefinition() throws Exception {
        return instantiateNoArgsClass(
                "com.penmate.backend.application.agent.tool.definition.QualityReviewToolDefinition"
        );
    }

    private static Object instantiateQualityReviewToolHandler(Object qualityReviewApplicationService) throws Exception {
        Class<?> clazz = loadClass("com.penmate.backend.application.agent.tool.handler.QualityReviewToolHandler");
        Class<?> serviceType = loadClass("com.penmate.backend.application.agent.tool.QualityReviewApplicationService");
        Constructor<?> constructor = clazz.getDeclaredConstructor(serviceType);
        constructor.setAccessible(true);
        return constructor.newInstance(qualityReviewApplicationService);
    }

    private static Object instantiateNoArgsClass(String fqcn) throws Exception {
        Class<?> clazz = loadClass(fqcn);
        Constructor<?> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Class<?> loadClass(String fqcn) {
        try {
            return Class.forName(fqcn);
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("Expected class to exist: " + fqcn, ex);
        }
    }

    private static Object readAccessor(Object target, String accessor) throws Exception {
        Method method = target.getClass().getMethod(accessor);
        method.setAccessible(true);
        return method.invoke(target);
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> castMap(Object value) {
        return (Map<?, ?>) value;
    }

    private static ToolCallResult execute(Object handler, ToolCallRequest request) throws Exception {
        Method executeMethod = handler.getClass().getMethod("execute", ToolCallRequest.class);
        executeMethod.setAccessible(true);
        return (ToolCallResult) executeMethod.invoke(handler, request);
    }

    private static ToolCallRequest request(String toolCallId, String toolArgsJson) {
        return new ToolCallRequest(
                9001L,
                8001L,
                6001L,
                "quality_review",
                toolArgsJson,
                1001L,
                "trace-" + toolCallId,
                "{}",
                toolCallId + "-8001",
                "trace-" + toolCallId + "-loop",
                0,
                toolCallId,
                "[]",
                "[]",
                "RESUME_LOOP",
                null
        );
    }

    private static String validArgsJson() {
        return """
                {
                  "draftText": "第三章初稿正文",
                  "userRequirements": ["保留第一人称", "维持紧张悬疑节奏"],
                  "personaProfile": ["女主冷静克制", "副官不掌握密道"],
                  "storyOutline": ["夜宴收到密令", "女主独自调查密道"],
                  "timelineConstraints": ["全章发生于同一夜晚"],
                  "worldRules": ["凡人不可直接施法", "禁术只能由祭司发动"],
                  "characterKnowledgeBoundaries": ["密道位置仅女主知晓", "密令全文仅皇帝与女主知晓"],
                  "currentRevisionRound": 1,
                  "maxRevisionRounds": 2
                }
                """;
    }

}
