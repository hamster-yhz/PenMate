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
                Map.entry("toolCode", "delete_book"),
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

        Class<?> approvalViewType = Class.forName("com.penmate.backend.domain.shared.model.ApprovalView");
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

    @Test
    void UT_INFRA_REALTIME_EVENT_SERVICE_PUBLISHES_AGENT_STATUS_EVENT_WITH_STAGE_AND_MESSAGE() throws Exception {
        ProjectWebSocketSessionRegistry sessionRegistry = new ProjectWebSocketSessionRegistry();
        GenerationSseEmitterHub emitterHub = new GenerationSseEmitterHub();
        RealtimeEventServiceImpl service = new RealtimeEventServiceImpl(sessionRegistry, emitterHub, new ObjectMapper());

        Method method = Arrays.stream(RealtimeEventServiceImpl.class.getMethods())
                .filter(candidate -> candidate.getName().equals("publishGenerationStatus"))
                .filter(candidate -> candidate.getParameterCount() == 5)
                .findFirst()
                .orElse(null);

        assertThat(method)
                .as("agent status SSE contract should expose stage and message")
                .isNotNull();
        if (method == null) {
            return;
        }

        method.invoke(service,
                9L,
                17L,
                "retrieving_context",
                "检索知识库参考资料",
                "running");

        Object state = loadTaskState(emitterHub, 17L);
        List<?> bufferedEvents = loadBufferedEvents(state);
        Object event = bufferedEvents.get(0);

        assertThat(readField(event, "eventName")).isEqualTo("generation.status");
        assertThat(readField(event, "data")).isEqualTo(Map.ofEntries(
                Map.entry("taskId", 17L),
                Map.entry("stage", "retrieving_context"),
                Map.entry("message", "检索知识库参考资料"),
                Map.entry("status", "running")
        ));
    }

    @Test
    void UT_INFRA_REALTIME_EVENT_SERVICE_PUBLISHES_STRUCTURED_RUNTIME_STATUS_EVENT_WITH_RECOVERY_ALIGNED_PAYLOAD() throws Exception {
        ProjectWebSocketSessionRegistry sessionRegistry = new ProjectWebSocketSessionRegistry();
        GenerationSseEmitterHub emitterHub = new GenerationSseEmitterHub();
        RealtimeEventServiceImpl service = new RealtimeEventServiceImpl(sessionRegistry, emitterHub, new ObjectMapper());

        Class<?> runtimeStatusViewType = tryLoadClass("com.penmate.backend.application.agent.runtime.RuntimeStatusView");
        Class<?> toolCallStatusViewType = tryLoadClass("com.penmate.backend.application.agent.runtime.ToolCallStatusView");
        Class<?> storyBibleApprovalViewType = tryLoadClass("com.penmate.backend.application.agent.runtime.StoryBibleApprovalView");
        Class<?> todoPlanViewType = tryLoadClass("com.penmate.backend.application.todo.TodoPlanView");
        Class<?> todoPlanItemViewType = tryLoadClass("com.penmate.backend.application.todo.TodoPlanItemView");
        Method method = Arrays.stream(RealtimeEventServiceImpl.class.getMethods())
                .filter(candidate -> candidate.getName().equals("publishTaskRuntimeStatus"))
                .filter(candidate -> candidate.getParameterCount() == 3)
                .findFirst()
                .orElse(null);

        assertThat(runtimeStatusViewType)
                .as("RuntimeStatusView should exist for structured runtime events")
                .isNotNull();
        assertThat(toolCallStatusViewType)
                .as("ToolCallStatusView should exist for structured runtime events")
                .isNotNull();
        assertThat(method)
                .as("RealtimeEventServiceImpl should expose publishTaskRuntimeStatus(Long, String, RuntimeStatusView)")
                .isNotNull();
        assertThat(storyBibleApprovalViewType)
                .as("StoryBibleApprovalView should exist for structured runtime events")
                .isNotNull();
        assertThat(todoPlanViewType)
                .as("TodoPlanView should exist for structured runtime events")
                .isNotNull();
        assertThat(todoPlanItemViewType)
                .as("TodoPlanItemView should exist for structured runtime events")
                .isNotNull();
        if (runtimeStatusViewType == null || toolCallStatusViewType == null || storyBibleApprovalViewType == null || todoPlanViewType == null || todoPlanItemViewType == null || method == null) {
            return;
        }

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

        Map<String, Object> approval = new java.util.LinkedHashMap<>();
        approval.put("approvalId", 42L);
        approval.put("approvalType", "STORY_BIBLE_REVIEW");
        Object storyBibleApproval = instantiateRecord(storyBibleApprovalViewType, Map.of(
                "approvalId", 42L,
                "approvalType", "STORY_BIBLE_UPDATE",
                "proposalSummary", "建议补充侍从知晓密令的设定",
                "entryKeys", List.of("maid.secret_order"),
                "nextAction", "await_approval"
        ));
        Object todoPlan = instantiateRecord(todoPlanViewType, Map.of(
                "planTitle", "第三章修订待办",
                "planSummary", "补齐密令来源链路",
                "recommendedNextAction", "apply_todo_plan",
                "items", List.of(instantiateRecord(todoPlanItemViewType, Map.of(
                        "title", "修复密令来源",
                        "description", "补充侍从转述桥段",
                        "priority", "P0",
                        "sourceType", "QUALITY_REVIEW",
                        "recommendedStatus", "TODO",
                        "suggestedAutoCreate", true,
                        "rationale", "避免剧情漏洞",
                        "acceptanceCriteria", List.of("密令来源明确"),
                        "dependsOn", List.of()
                )))
        ));

        Map<String, Object> valuesByName = new java.util.LinkedHashMap<>();
        valuesByName.put("taskId", 17L);
        valuesByName.put("sessionId", 90001L);
        valuesByName.put("turnId", 50001L);
        valuesByName.put("phase", "story_bible_review");
        valuesByName.put("message", "正在整理故事圣经");
        valuesByName.put("toolCall", toolCall);
        valuesByName.put("approval", approval);
        valuesByName.put("storyBibleApproval", storyBibleApproval);
        valuesByName.put("todoPlan", todoPlan);
        valuesByName.put("recoverable", true);
        valuesByName.put("nextAction", "review_story_bible");
        Object runtimeStatus = instantiateRecord(runtimeStatusViewType, valuesByName);

        method.invoke(service, 9L, "generation.status", runtimeStatus);

        Object state = loadTaskState(emitterHub, 17L);
        List<?> bufferedEvents = loadBufferedEvents(state);
        Object event = bufferedEvents.get(0);

        Map<String, Object> expectedPayload = new java.util.LinkedHashMap<>();
        expectedPayload.put("taskId", 17L);
        expectedPayload.put("sessionId", 90001L);
        expectedPayload.put("turnId", 50001L);
        expectedPayload.put("phase", "story_bible_review");
        expectedPayload.put("message", "正在整理故事圣经");
        expectedPayload.put("toolCall", Map.ofEntries(
                Map.entry("toolCallId", "call_quality_1"),
                Map.entry("toolCode", "quality_review"),
                Map.entry("toolName", "质量审查"),
                Map.entry("status", "running"),
                Map.entry("iteration", 2),
                Map.entry("argumentsPreview", Map.of("draftId", "draft-17")),
                Map.entry("output", Map.of("score", 82)),
                Map.entry("errorMessage", "")
        ));
        expectedPayload.put("approval", Map.ofEntries(
                Map.entry("approvalId", 42L),
                Map.entry("approvalType", "STORY_BIBLE_REVIEW")
        ));
        expectedPayload.put("storyBibleApproval", Map.ofEntries(
                Map.entry("approvalId", 42L),
                Map.entry("approvalType", "STORY_BIBLE_UPDATE"),
                Map.entry("proposalSummary", "建议补充侍从知晓密令的设定"),
                Map.entry("entryKeys", List.of("maid.secret_order")),
                Map.entry("nextAction", "await_approval")
        ));
        expectedPayload.put("todoPlan", Map.ofEntries(
                Map.entry("planTitle", "第三章修订待办"),
                Map.entry("planSummary", "补齐密令来源链路"),
                Map.entry("recommendedNextAction", "apply_todo_plan"),
                Map.entry("items", List.of(Map.ofEntries(
                        Map.entry("title", "修复密令来源"),
                        Map.entry("description", "补充侍从转述桥段"),
                        Map.entry("priority", "P0"),
                        Map.entry("sourceType", "QUALITY_REVIEW"),
                        Map.entry("recommendedStatus", "TODO"),
                        Map.entry("suggestedAutoCreate", true),
                        Map.entry("rationale", "避免剧情漏洞"),
                        Map.entry("acceptanceCriteria", List.of("密令来源明确")),
                        Map.entry("dependsOn", List.of())
                )))
        ));
        expectedPayload.put("recoverable", true);
        expectedPayload.put("nextAction", "review_story_bible");

        assertThat(readField(event, "eventName")).isEqualTo("generation.status");
        assertThat(readField(event, "data")).isEqualTo(expectedPayload);
    }

    @Test
    void UT_INFRA_REALTIME_EVENT_SERVICE_SHOULD_PARSE_TODO_CRUD_TOOL_OUTPUT_JSON_IN_STRUCTURED_RUNTIME_EVENT() throws Exception {
        ProjectWebSocketSessionRegistry sessionRegistry = new ProjectWebSocketSessionRegistry();
        GenerationSseEmitterHub emitterHub = new GenerationSseEmitterHub();
        RealtimeEventServiceImpl service = new RealtimeEventServiceImpl(sessionRegistry, emitterHub, new ObjectMapper());

        Class<?> runtimeStatusViewType = tryLoadClass("com.penmate.backend.application.agent.runtime.RuntimeStatusView");
        Class<?> toolCallStatusViewType = tryLoadClass("com.penmate.backend.application.agent.runtime.ToolCallStatusView");
        Method method = Arrays.stream(RealtimeEventServiceImpl.class.getMethods())
                .filter(candidate -> candidate.getName().equals("publishTaskRuntimeStatus"))
                .filter(candidate -> candidate.getParameterCount() == 3)
                .findFirst()
                .orElse(null);

        assertThat(runtimeStatusViewType)
                .as("RuntimeStatusView should exist for structured runtime events")
                .isNotNull();
        assertThat(toolCallStatusViewType)
                .as("ToolCallStatusView should exist for structured runtime events")
                .isNotNull();
        assertThat(method)
                .as("RealtimeEventServiceImpl should expose publishTaskRuntimeStatus(Long, String, RuntimeStatusView)")
                .isNotNull();
        if (runtimeStatusViewType == null || toolCallStatusViewType == null || method == null) {
            return;
        }

        Object toolCall = instantiateRecord(toolCallStatusViewType, Map.of(
                "toolCallId", "call_todo_1",
                "toolCode", "todo_crud",
                "toolName", "待办 CRUD",
                "status", "done",
                "iteration", 1,
                "argumentsPreview", Map.of("operation", "update", "todoId", "20031"),
                "output", "{\"operation\":\"update\",\"todoId\":\"20031\",\"sessionId\":\"9\",\"taskId\":\"77\",\"title\":\"修订第三章开场\",\"sourceType\":\"PLANNING\",\"todoStatus\":\"BLOCKED\"}",
                "errorMessage", ""
        ));

        Map<String, Object> runtimeStatusValues = new java.util.LinkedHashMap<>();
        runtimeStatusValues.put("taskId", 17L);
        runtimeStatusValues.put("sessionId", 90001L);
        runtimeStatusValues.put("turnId", 50001L);
        runtimeStatusValues.put("phase", "tool_call");
        runtimeStatusValues.put("message", "待办 CRUD");
        runtimeStatusValues.put("toolCall", toolCall);
        runtimeStatusValues.put("approval", null);
        runtimeStatusValues.put("storyBibleApproval", null);
        runtimeStatusValues.put("todoPlan", null);
        runtimeStatusValues.put("recoverable", true);
        runtimeStatusValues.put("nextAction", "continue_tool_loop");
        Object runtimeStatus = instantiateRecord(runtimeStatusViewType, runtimeStatusValues);

        method.invoke(service, 9L, "generation.tool_call", runtimeStatus);

        Object state = loadTaskState(emitterHub, 17L);
        List<?> bufferedEvents = loadBufferedEvents(state);
        Object event = bufferedEvents.get(0);

        Map<String, Object> expectedPayload = new java.util.LinkedHashMap<>();
        expectedPayload.put("taskId", 17L);
        expectedPayload.put("sessionId", 90001L);
        expectedPayload.put("turnId", 50001L);
        expectedPayload.put("phase", "tool_call");
        expectedPayload.put("message", "待办 CRUD");
        expectedPayload.put("toolCall", Map.ofEntries(
                Map.entry("toolCallId", "call_todo_1"),
                Map.entry("toolCode", "todo_crud"),
                Map.entry("toolName", "待办 CRUD"),
                Map.entry("status", "done"),
                Map.entry("iteration", 1),
                Map.entry("argumentsPreview", Map.of("operation", "update", "todoId", "20031")),
                Map.entry("output", Map.ofEntries(
                        Map.entry("operation", "update"),
                        Map.entry("todoId", "20031"),
                        Map.entry("sessionId", "9"),
                        Map.entry("taskId", "77"),
                        Map.entry("title", "修订第三章开场"),
                        Map.entry("sourceType", "PLANNING"),
                        Map.entry("todoStatus", "BLOCKED")
                )),
                Map.entry("errorMessage", "")
        ));
        expectedPayload.put("approval", null);
        expectedPayload.put("storyBibleApproval", null);
        expectedPayload.put("todoPlan", null);
        expectedPayload.put("recoverable", true);
        expectedPayload.put("nextAction", "continue_tool_loop");

        assertThat(readField(event, "eventName")).isEqualTo("generation.tool_call");
        assertThat(readField(event, "data")).isEqualTo(expectedPayload);
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

    private static Class<?> tryLoadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }

    private static Object instantiateRecord(Class<?> type, Map<String, Object> valuesByName) throws Exception {
        java.lang.reflect.RecordComponent[] components = type.getRecordComponents();
        Class<?>[] parameterTypes = Arrays.stream(components)
                .map(java.lang.reflect.RecordComponent::getType)
                .toArray(Class<?>[]::new);
        Object[] arguments = Arrays.stream(components)
                .map(component -> valuesByName.get(component.getName()))
                .toArray();
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }
}
