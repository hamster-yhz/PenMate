package com.penmate.backend.infrastructure.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeEventServiceImplTest {

    @Test
    void UT_INFRA_REALTIME_EVENT_SERVICE_PUBLISHES_TOOL_CALL_WITH_EXTENDED_CONTRACT_FIELDS() throws Exception {
        ProjectWebSocketSessionRegistry sessionRegistry = new ProjectWebSocketSessionRegistry();
        GenerationSseEmitterHub emitterHub = new GenerationSseEmitterHub();
        RealtimeEventServiceImpl service = new RealtimeEventServiceImpl(sessionRegistry, emitterHub, new ObjectMapper());

        Method method = Arrays.stream(RealtimeEventServiceImpl.class.getMethods())
                .filter(candidate -> candidate.getName().equals("publishGenerationToolCall"))
                .filter(candidate -> candidate.getParameterCount() == 12)
                .findFirst()
                .orElse(null);

        assertThat(method)
                .as("extended tool_call contract should expose iteration parameter")
                .isNotNull();
        if (method == null) {
            return;
        }

        method.invoke(service,
                9L,
                17L,
                "call_9",
                "book_crud",
                "delete_book",
                "waiting_approval",
                42L,
                "BOOK_DELETE",
                2,
                Map.of("operation", "delete"),
                null,
                null);

        Object state = loadTaskState(emitterHub, 17L);
        List<?> bufferedEvents = loadBufferedEvents(state);
        Object event = bufferedEvents.get(0);

        assertThat(readField(event, "eventName")).isEqualTo("generation.tool_call");
        assertThat(readField(event, "data")).isEqualTo(Map.ofEntries(
                Map.entry("taskId", 17L),
                Map.entry("toolCallId", "call_9"),
                Map.entry("pluginCode", "book_crud"),
                Map.entry("toolCode", "book_crud"),
                Map.entry("toolName", "delete_book"),
                Map.entry("status", "waiting_approval"),
                Map.entry("approvalId", 42L),
                Map.entry("approvalType", "BOOK_DELETE"),
                Map.entry("iteration", 2),
                Map.entry("argumentsPreview", Map.of("operation", "delete")),
                Map.entry("errorMsg", ""),
                Map.entry("output", "")
        ));
    }

    @Test
    void UT_INFRA_REALTIME_EVENT_SERVICE_PUBLISHES_WAITING_APPROVAL_WITH_RESUME_METADATA() throws Exception {
        ProjectWebSocketSessionRegistry sessionRegistry = new ProjectWebSocketSessionRegistry();
        GenerationSseEmitterHub emitterHub = new GenerationSseEmitterHub();
        RealtimeEventServiceImpl service = new RealtimeEventServiceImpl(sessionRegistry, emitterHub, new ObjectMapper());

        Method method = Arrays.stream(RealtimeEventServiceImpl.class.getMethods())
                .filter(candidate -> candidate.getName().equals("publishGenerationWaitingApproval"))
                .filter(candidate -> candidate.getParameterCount() == 8)
                .findFirst()
                .orElse(null);

        assertThat(method)
                .as("extended waiting_approval contract should expose resumeMode parameter")
                .isNotNull();
        if (method == null) {
            return;
        }

        Class<?> approvalViewType = Class.forName("com.penmate.backend.application.agent.tool.definition.ToolApprovalView");
        Object approvalView = approvalViewType
                .getDeclaredConstructor(String.class, String.class, Integer.class, String.class, String.class)
                .newInstance("book_crud", "书籍 CRUD", 2, "BOOK_DELETE", "delete");

        method.invoke(service,
                9L,
                17L,
                "call_9",
                42L,
                "BOOK_DELETE",
                Map.of("approvalType", "BOOK_DELETE", "target", "project-9"),
                "RESUME_LOOP",
                approvalView);

        Object state = loadTaskState(emitterHub, 17L);
        List<?> bufferedEvents = loadBufferedEvents(state);
        Object event = bufferedEvents.get(0);

        assertThat(readField(event, "eventName")).isEqualTo("generation.waiting_approval");
        assertThat(readField(event, "data")).isEqualTo(Map.ofEntries(
                Map.entry("taskId", 17L),
                Map.entry("toolCallId", "call_9"),
                Map.entry("approvalId", 42L),
                Map.entry("approvalType", "BOOK_DELETE"),
                Map.entry("toolCode", "book_crud"),
                Map.entry("toolDisplayName", "书籍 CRUD"),
                Map.entry("riskLevel", 2),
                Map.entry("operationCode", "delete"),
                Map.entry("approvalPreview", Map.of("approvalType", "BOOK_DELETE", "target", "project-9")),
                Map.entry("resumeMode", "RESUME_LOOP"),
                Map.entry("status", "waiting_approval")
        ));
    }

    private static Object loadTaskState(GenerationSseEmitterHub emitterHub, Long taskId) throws Exception {
        Field statesField = GenerationSseEmitterHub.class.getDeclaredField("statesByTask");
        statesField.setAccessible(true);
        Map<?, ?> statesByTask = (Map<?, ?>) statesField.get(emitterHub);
        return statesByTask.get(taskId);
    }

    private static List<?> loadBufferedEvents(Object state) throws Exception {
        Field bufferedEventsField = state.getClass().getDeclaredField("bufferedEvents");
        bufferedEventsField.setAccessible(true);
        return List.copyOf((java.util.Collection<?>) bufferedEventsField.get(state));
    }

    private static Object readField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
