package com.penmate.backend.application.agent.runtime;

import com.penmate.backend.domain.shared.service.RealtimeEventService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class TaskRuntimeStatusPublisherTest {

    @Test
    void UT_APP_TASK_RUNTIME_STATUS_PUBLISHER_SHOULD_EXPOSE_RECOVERY_ALIGNED_RUNTIME_STATUS_VIEW() {
        Class<?> runtimeStatusViewType = loadRequiredClass("com.penmate.backend.application.agent.runtime.RuntimeStatusView");
        Class<?> toolCallStatusViewType = loadRequiredClass("com.penmate.backend.application.agent.runtime.ToolCallStatusView");
        Class<?> storyBibleApprovalViewType = loadRequiredClass("com.penmate.backend.application.agent.runtime.StoryBibleApprovalView");

        assertThat(runtimeStatusViewType.isRecord()).isTrue();
        assertThat(toolCallStatusViewType.isRecord()).isTrue();
        assertThat(storyBibleApprovalViewType.isRecord()).isTrue();
        assertThat(recordComponentNames(runtimeStatusViewType)).containsExactly(
                "taskId",
                "sessionId",
                "turnId",
                "phase",
                "message",
                "toolCall",
                "approval",
                "storyBibleApproval",
                "recoverable",
                "nextAction"
        );
    }

    @Test
    void UT_APP_TASK_RUNTIME_STATUS_PUBLISHER_SHOULD_DECLARE_EVENT_SPECIFIC_PUBLICATION_PORTS() {
        Class<?> publisherType = loadRequiredClass("com.penmate.backend.application.agent.runtime.TaskRuntimeStatusPublisher");
        Class<?> runtimeStatusViewType = loadRequiredClass("com.penmate.backend.application.agent.runtime.RuntimeStatusView");

        assertThat(publisherType.isInterface()).isTrue();
        assertPublicationMethod(publisherType, "publishStarted", runtimeStatusViewType);
        assertPublicationMethod(publisherType, "publishStatus", runtimeStatusViewType);
        assertPublicationMethod(publisherType, "publishToolCall", runtimeStatusViewType);
        assertPublicationMethod(publisherType, "publishWaitingApproval", runtimeStatusViewType);
        assertPublicationMethod(publisherType, "publishDone", runtimeStatusViewType);
        assertPublicationMethod(publisherType, "publishFailed", runtimeStatusViewType);
    }

    @Test
    void UT_APP_TASK_RUNTIME_STATUS_PUBLISHER_SHOULD_MAP_EVENT_SPECIFIC_PORTS_TO_REALTIME_RUNTIME_EVENT_PROTOCOL() throws Exception {
        Class<?> publisherType = loadRequiredClass("com.penmate.backend.application.agent.runtime.TaskRuntimeStatusPublisher");
        Class<?> implementationType = loadRequiredClass("com.penmate.backend.application.agent.runtime.RealtimeTaskRuntimeStatusPublisher");
        Class<?> runtimeStatusViewType = loadRequiredClass("com.penmate.backend.application.agent.runtime.RuntimeStatusView");
        Class<?> toolCallStatusViewType = loadRequiredClass("com.penmate.backend.application.agent.runtime.ToolCallStatusView");

        AtomicReference<String> invokedMethodName = new AtomicReference<>();
        AtomicReference<Object[]> invokedArguments = new AtomicReference<>();
        RealtimeEventService realtimeEventService = (RealtimeEventService) Proxy.newProxyInstance(
                RealtimeEventService.class.getClassLoader(),
                new Class[]{RealtimeEventService.class},
                (proxy, method, args) -> {
                    invokedMethodName.set(method.getName());
                    invokedArguments.set(args == null ? new Object[0] : args);
                    return null;
                }
        );

        Constructor<?> constructor = implementationType.getDeclaredConstructor(RealtimeEventService.class);
        Object publisher = constructor.newInstance(realtimeEventService);
        Object toolCall = instantiateRecord(toolCallStatusViewType, Map.of(
                "toolCallId", "call_quality_1",
                "toolCode", "quality_review",
                "toolName", "质量审查",
                "status", "running",
                "iteration", 2,
                "argumentsPreview", Map.of("draftId", "draft-17"),
                "output", Map.of("score", 82),
                "errorMessage", ""
        ));
        Map<String, Object> runtimeStatusValues = new LinkedHashMap<>();
        runtimeStatusValues.put("taskId", 17L);
        runtimeStatusValues.put("sessionId", 90001L);
        runtimeStatusValues.put("turnId", 50001L);
        runtimeStatusValues.put("phase", "tool_call");
        runtimeStatusValues.put("message", "正在审查质量");
        runtimeStatusValues.put("toolCall", toolCall);
        runtimeStatusValues.put("approval", Map.of("approvalId", 42L, "approvalType", "QUALITY_REVIEW"));
        runtimeStatusValues.put("storyBibleApproval", null);
        runtimeStatusValues.put("recoverable", true);
        runtimeStatusValues.put("nextAction", "wait_tool_result");
        Object runtimeStatus = instantiateRecord(runtimeStatusViewType, runtimeStatusValues);

        assertPublisherEventMapping(publisherType, publisher, runtimeStatusViewType, runtimeStatus,
                "publishStarted", "generation.started", invokedMethodName, invokedArguments);
        assertPublisherEventMapping(publisherType, publisher, runtimeStatusViewType, runtimeStatus,
                "publishStatus", "generation.status", invokedMethodName, invokedArguments);
        assertPublisherEventMapping(publisherType, publisher, runtimeStatusViewType, runtimeStatus,
                "publishToolCall", "generation.tool_call", invokedMethodName, invokedArguments);
        assertPublisherEventMapping(publisherType, publisher, runtimeStatusViewType, runtimeStatus,
                "publishWaitingApproval", "generation.waiting_approval", invokedMethodName, invokedArguments);
        assertPublisherEventMapping(publisherType, publisher, runtimeStatusViewType, runtimeStatus,
                "publishDone", "generation.done", invokedMethodName, invokedArguments);
        assertPublisherEventMapping(publisherType, publisher, runtimeStatusViewType, runtimeStatus,
                "publishFailed", "generation.failed", invokedMethodName, invokedArguments);
    }

    private static void assertPublisherEventMapping(Class<?> publisherType,
                                                    Object publisher,
                                                    Class<?> runtimeStatusViewType,
                                                    Object runtimeStatus,
                                                    String methodName,
                                                    String expectedEventType,
                                                    AtomicReference<String> invokedMethodName,
                                                    AtomicReference<Object[]> invokedArguments) throws Exception {
        invokedMethodName.set(null);
        invokedArguments.set(null);

        Method method = publisherType.getMethod(methodName, Long.class, runtimeStatusViewType);
        method.invoke(publisher, 9L, runtimeStatus);

        assertThat(invokedMethodName.get()).isEqualTo("publishTaskRuntimeStatus");
        assertThat(invokedArguments.get()).containsExactly(9L, expectedEventType, runtimeStatus);
    }

    private static void assertPublicationMethod(Class<?> publisherType, String methodName, Class<?> runtimeStatusViewType) {
        Method method = Arrays.stream(publisherType.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElse(null);
        assertThat(method)
                .as("publisher should declare %s(Long, RuntimeStatusView)", methodName)
                .isNotNull();
        if (method == null) {
            return;
        }
        assertThat(method.getParameterTypes()).containsExactly(Long.class, runtimeStatusViewType);
    }

    private static List<String> recordComponentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    private static Object instantiateRecord(Class<?> type, Map<String, Object> valuesByName) throws Exception {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] parameterTypes = Arrays.stream(components)
                .map(RecordComponent::getType)
                .toArray(Class<?>[]::new);
        Object[] arguments = Arrays.stream(components)
                .map(component -> valuesByName.get(component.getName()))
                .toArray();
        Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

    private static Class<?> loadRequiredClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            fail("Required runtime status contract type is missing: " + className, ex);
            return null;
        }
    }
}
