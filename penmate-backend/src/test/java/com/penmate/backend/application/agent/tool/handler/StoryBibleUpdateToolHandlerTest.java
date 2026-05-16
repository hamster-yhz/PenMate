package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StoryBibleUpdateToolHandlerTest {

    @Test
    void UT_APP_AGENT_STORY_BIBLE_UPDATE_TOOL_DEFINITION_SHOULD_REQUIRE_APPROVAL_FOR_CREATE_UPDATE_DELETE_OPERATIONS() throws Exception {
        Object definition = instantiateNoArgsClass(
                "com.penmate.backend.application.agent.tool.definition.StoryBibleUpdateToolDefinition"
        );
        Object descriptor = definition.getClass().getMethod("descriptor").invoke(definition);

        assertThat(readAccessor(descriptor, "toolCode")).isEqualTo("story_bible_update");

        Object presentation = readAccessor(descriptor, "presentation");
        assertThat(readAccessor(presentation, "displayName")).isEqualTo("故事圣经更新");

        Object governancePolicy = readAccessor(descriptor, "governancePolicy");
        Object defaultDecision = readAccessor(governancePolicy, "defaultDecision");
        assertThat(readAccessor(defaultDecision, "approvalRequired")).isEqualTo(false);

        @SuppressWarnings("unchecked")
        Map<String, Object> operationPolicies = (Map<String, Object>) readAccessor(governancePolicy, "operationPolicies");
        assertThat(operationPolicies.keySet()).contains("create", "update", "delete");

        assertApprovalRequired(operationPolicies.get("create"), "STORY_BIBLE_CREATE");
        assertApprovalRequired(operationPolicies.get("update"), "STORY_BIBLE_UPDATE");
        assertApprovalRequired(operationPolicies.get("delete"), "STORY_BIBLE_DELETE");
    }

    @Test
    void UT_APP_AGENT_STORY_BIBLE_UPDATE_TOOL_HANDLER_SHOULD_EXPOSE_STABLE_TOOL_CODE() throws Exception {
        Class<?> serviceType = loadClass("com.penmate.backend.application.agent.tool.StoryBibleUpdateApplicationService");
        Object service = Proxy.newProxyInstance(
                serviceType.getClassLoader(),
                new Class<?>[]{serviceType},
                (proxy, method, args) -> ToolCallResult.success("{}")
        );
        Object handler = instantiateStoryBibleUpdateToolHandler(service);

        assertThat(handler.getClass().getMethod("toolCode").invoke(handler)).isEqualTo("story_bible_update");
    }

    @Test
    void UT_APP_AGENT_STORY_BIBLE_UPDATE_TOOL_HANDLER_EXECUTE_SHOULD_DELEGATE_TO_APPLICATION_SERVICE() throws Exception {
        Class<?> serviceType = loadClass("com.penmate.backend.application.agent.tool.StoryBibleUpdateApplicationService");
        ToolCallResult delegatedResult = ToolCallResult.success("{\"delegated\":true}");
        Object service = Proxy.newProxyInstance(
                serviceType.getClassLoader(),
                new Class<?>[]{serviceType},
                (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) {
                        assertThat(args).hasSize(1);
                        assertThat(args[0]).isInstanceOf(ToolCallRequest.class);
                        return delegatedResult;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );

        Object handler = instantiateStoryBibleUpdateToolHandler(service);
        ToolCallResult result = execute(handler, request("call-story-bible-delegate", "{\"operation\":\"list\"}"));

        assertThat(result).isSameAs(delegatedResult);
    }

    private static void assertApprovalRequired(Object operationPolicy, String approvalType) throws Exception {
        assertThat(operationPolicy).isNotNull();
        Object decision = readAccessor(operationPolicy, "decision");
        assertThat(readAccessor(decision, "approvalRequired")).isEqualTo(true);
        assertThat(readAccessor(decision, "approvalType")).isEqualTo(approvalType);
    }

    private static Object instantiateNoArgsClass(String fqcn) throws Exception {
        Class<?> clazz = loadClass(fqcn);
        Constructor<?> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Object instantiateStoryBibleUpdateToolHandler(Object storyBibleUpdateApplicationService) throws Exception {
        Class<?> clazz = loadClass("com.penmate.backend.application.agent.tool.handler.StoryBibleUpdateToolHandler");
        Class<?> serviceType = loadClass("com.penmate.backend.application.agent.tool.StoryBibleUpdateApplicationService");
        Constructor<?> constructor = clazz.getDeclaredConstructor(serviceType);
        constructor.setAccessible(true);
        return constructor.newInstance(storyBibleUpdateApplicationService);
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
                "story_bible_update",
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
}
