package com.penmate.backend.application.agent;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextEnhancerAgentToolHandlerTest {

    @Test
    void UT_APP_AGENT_CONTEXT_ENHANCER_TOOL_HANDLER_DELEGATES_TO_PLUGIN_COORDINATOR() {
        PluginToolCoordinator pluginToolCoordinator = mock(PluginToolCoordinator.class);
        when(pluginToolCoordinator.execute(org.mockito.ArgumentMatchers.any()))
                .thenReturn(ToolExecutionResult.success("plugin-a", "context_enhancer", "增强上下文"));

        Object handler = instantiateHandler(pluginToolCoordinator);
        ToolInvocationRequest request = new ToolInvocationRequest(
                10001L,
                8013L,
                7001L,
                "context_enhancer",
                "{\"prompt\":\"补充帝国冲突\"}",
                1001L,
                "trace-context-handler",
                "{}",
                "context-enhancer-8013"
        );

        Object result = execute(handler, request);

        assertThat(invokeAccessor(result, "status")).isEqualTo("SUCCESS");
        assertThat(invokeAccessor(result, "toolOutput")).isEqualTo("增强上下文");

        ArgumentCaptor<ToolExecutionRequest> captor = ArgumentCaptor.forClass(ToolExecutionRequest.class);
        verify(pluginToolCoordinator).execute(captor.capture());
        assertThat(captor.getValue().projectId()).isEqualTo(10001L);
        assertThat(captor.getValue().taskId()).isEqualTo(8013L);
        assertThat(captor.getValue().prompt()).isEqualTo("补充帝国冲突");
        assertThat(captor.getValue().traceId()).isEqualTo("trace-context-handler");
    }

    @Test
    void UT_APP_AGENT_CONTEXT_ENHANCER_TOOL_HANDLER_DOES_NOT_KEEP_DIRECT_OBJECT_MAPPER_DEPENDENCY() throws Exception {
        Class<?> handlerType = Class.forName("com.penmate.backend.application.agent.ContextEnhancerAgentToolHandler");

        assertThat(Arrays.stream(handlerType.getDeclaredFields())
                .map(Field::getType)
                .toList())
                .noneMatch(type -> type.equals(com.fasterxml.jackson.databind.ObjectMapper.class));
    }

    private Object instantiateHandler(PluginToolCoordinator pluginToolCoordinator) {
        try {
            Class<?> type = Class.forName("com.penmate.backend.application.agent.ContextEnhancerAgentToolHandler");
            Constructor<?> constructor = type.getDeclaredConstructor(PluginToolCoordinator.class);
            constructor.setAccessible(true);
            return constructor.newInstance(pluginToolCoordinator);
        } catch (Exception ex) {
            throw new AssertionError("expected context enhancer handler to be constructible", ex);
        }
    }

    private Object execute(Object handler, ToolInvocationRequest request) {
        try {
            Method method = handler.getClass().getMethod("execute", ToolInvocationRequest.class);
            return method.invoke(handler, request);
        } catch (Exception ex) {
            throw new AssertionError("expected context enhancer handler execution to succeed", ex);
        }
    }

    private Object invokeAccessor(Object target, String accessorName) {
        try {
            Method method = target.getClass().getMethod(accessorName);
            return method.invoke(target);
        } catch (Exception ex) {
            throw new AssertionError("expected accessor invocation to succeed: " + accessorName, ex);
        }
    }
}
