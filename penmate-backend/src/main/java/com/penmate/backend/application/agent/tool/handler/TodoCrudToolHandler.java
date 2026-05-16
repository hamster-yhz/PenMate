package com.penmate.backend.application.agent.tool.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.orchestration.AgentTaskRuntimeUpdater;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.todo.TodoCrudApplicationService;
import com.penmate.backend.domain.todo.model.SessionTodo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Todo CRUD tool 处理器。
 */
@Component
@RequiredArgsConstructor
public class TodoCrudToolHandler implements AgentToolHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TodoCrudApplicationService todoCrudApplicationService;

    @Override
    public String toolCode() {
        return "todo_crud";
    }

    @Override
    public void validate(ToolCallRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
    }

    @Override
    public ToolCallResult execute(ToolCallRequest request) {
        if (request == null) {
            return new ToolCallResult("FAILED", null, null, "TODO_CRUD_FAILED", "request must not be null");
        }
        try {
            Map<String, Object> args = OBJECT_MAPPER.readValue(request.toolArgsJson(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
            String operation = stringValue(args.get("operation")).toLowerCase();
            Long sessionId = longValue(args.get("sessionId"));
            Long todoId = longValue(args.get("todoId"));
            SessionTodo candidate = buildCandidate(args);
            if ("create".equals(operation)) {
                SessionTodo created = todoCrudApplicationService.createTodo(
                        request.projectId(),
                        sessionId,
                        longValue(args.get("taskId")) != null ? longValue(args.get("taskId")) : request.taskId(),
                        candidate,
                        request.operatorId(),
                        request.traceId()
                );
                return ToolCallResult.success(AgentTaskRuntimeUpdater.toSnapshotJson(buildTodoOutput("create", created)));
            }
            if ("list".equals(operation)) {
                java.util.List<SessionTodo> todos = todoCrudApplicationService.listSessionTodos(
                        request.projectId(),
                        sessionId,
                        nullableStringValue(args.get("todoStatus"))
                );
                Map<String, Object> output = new LinkedHashMap<>();
                output.put("operation", "list");
                output.put("sessionId", stringifyBusinessId(sessionId));
                output.put("items", todos == null ? java.util.List.of() : todos.stream().map(todo -> buildTodoOutput(null, todo)).toList());
                return ToolCallResult.success(AgentTaskRuntimeUpdater.toSnapshotJson(output));
            }
            if ("update".equals(operation)) {
                SessionTodo updated = todoCrudApplicationService.updateTodo(
                        request.projectId(),
                        sessionId,
                        todoId,
                        request.taskId(),
                        candidate,
                        request.operatorId(),
                        request.traceId()
                );
                return ToolCallResult.success(AgentTaskRuntimeUpdater.toSnapshotJson(buildTodoOutput("update", updated)));
            }
            if ("complete".equals(operation)) {
                SessionTodo completed = todoCrudApplicationService.completeTodo(
                        request.projectId(),
                        sessionId,
                        todoId,
                        request.operatorId(),
                        request.traceId()
                );
                return ToolCallResult.success(AgentTaskRuntimeUpdater.toSnapshotJson(buildTodoOutput("complete", completed)));
            }
            if ("delete".equals(operation)) {
                todoCrudApplicationService.deleteTodo(
                        request.projectId(),
                        sessionId,
                        todoId,
                        request.operatorId(),
                        request.traceId()
                );
                Map<String, Object> output = new LinkedHashMap<>();
                output.put("operation", "delete");
                output.put("todoId", stringifyBusinessId(todoId));
                output.put("sessionId", stringifyBusinessId(sessionId));
                output.put("deleted", true);
                return ToolCallResult.success(AgentTaskRuntimeUpdater.toSnapshotJson(output));
            }
            return new ToolCallResult("FAILED", null, null, "TODO_CRUD_FAILED", "unsupported todo crud operation: " + operation);
        } catch (Exception ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "todo crud execution failed"
                    : ex.getMessage();
            return new ToolCallResult("FAILED", null, null, "TODO_CRUD_FAILED", message);
        }
    }

    private SessionTodo buildCandidate(Map<String, Object> args) {
        SessionTodo candidate = new SessionTodo();
        candidate.setTitle(stringValue(args.get("title")));
        candidate.setDescription(nullableStringValue(args.get("description")));
        candidate.setSourceType(stringValue(args.get("sourceType")));
        candidate.setTodoStatus(stringValue(args.get("todoStatus")));
        return candidate;
    }

    private Map<String, Object> buildTodoOutput(String operation, SessionTodo todo) {
        Map<String, Object> output = new LinkedHashMap<>();
        if (operation != null && !operation.isBlank()) {
            output.put("operation", operation);
        }
        output.put("todoId", stringifyBusinessId(todo == null ? null : todo.getTodoId()));
        output.put("projectId", stringifyBusinessId(todo == null ? null : todo.getProjectId()));
        output.put("sessionId", stringifyBusinessId(todo == null ? null : todo.getSessionId()));
        output.put("taskId", stringifyBusinessId(todo == null ? null : todo.getTaskId()));
        output.put("title", todo == null ? null : todo.getTitle());
        output.put("description", todo == null ? null : todo.getDescription());
        output.put("sourceType", todo == null ? null : todo.getSourceType());
        output.put("todoStatus", todo == null ? null : todo.getTodoStatus());
        output.put("completedAt", todo == null || todo.getCompletedAt() == null ? null : todo.getCompletedAt().toString());
        output.put("updatedAt", todo == null || todo.getUpdatedAt() == null ? null : todo.getUpdatedAt().toString());
        return output;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String nullableStringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : Long.valueOf(normalized);
    }

    private String stringifyBusinessId(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}
