package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.common.serialization.JsonValues;
import com.penmate.backend.application.todo.TodoCrudApplicationService;
import com.penmate.backend.domain.todo.model.SessionTodo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Todo CRUD tool 处理器。
 */
@Component
@RequiredArgsConstructor
public class TodoCrudToolHandler implements AgentToolHandler {

    private final TodoCrudApplicationService todoCrudApplicationService;
    private final JsonCodec jsonCodec;

    @Override
    public String toolCode() {
        return "todo_crud";
    }

    @Override
    public boolean mutatesState(ToolCallRequest request) {
        String operation = JsonValues.string(jsonCodec.readObject(request.toolArgsJson()), "operation");
        return !"list".equalsIgnoreCase(operation);
    }

    @Override
    public void validate(ToolCallRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        try {
            Map<String, Object> args = jsonCodec.readObject(request.toolArgsJson());
            String operation = JsonValues.string(args, "operation");
            if (operation == null || operation.isBlank()) {
                throw new IllegalArgumentException("operation is required");
            }
            Long sessionId = JsonValues.longValue(args, "sessionId");
            if (sessionId == null) {
                throw new IllegalArgumentException("sessionId is required");
            }
            if (sessionId < 1) {
                throw new IllegalArgumentException("sessionId must be greater than or equal to 1");
            }
            if (!("list".equalsIgnoreCase(operation)
                    || "create".equalsIgnoreCase(operation)
                    || "update".equalsIgnoreCase(operation)
                    || "complete".equalsIgnoreCase(operation)
                    || "delete".equalsIgnoreCase(operation))) {
                throw new IllegalArgumentException("Unsupported operation: " + operation);
            }
            if ("list".equalsIgnoreCase(operation)) {
                rejectUnexpectedFields(args, operation, Set.of("operation", "sessionId", "todoStatus"));
            }
            if ("create".equalsIgnoreCase(operation)) {
                rejectUnexpectedFields(args, operation, Set.of("operation", "sessionId", "sourceRunId", "title", "description", "sourceType", "todoStatus"));
                validateOptionalLong(args, "sourceRunId");
                requireNonBlank(args, "title");
                requireNonBlank(args, "sourceType");
                requireNonBlank(args, "todoStatus");
            }
            if ("update".equalsIgnoreCase(operation)) {
                rejectUnexpectedFields(args, operation, Set.of("operation", "sessionId", "todoId", "sourceRunId", "title", "description", "sourceType", "todoStatus"));
                requireLong(args, "todoId");
                validateOptionalLong(args, "sourceRunId");
                requireNonBlank(args, "title");
                requireNonBlank(args, "sourceType");
                requireNonBlank(args, "todoStatus");
            }
            if ("complete".equalsIgnoreCase(operation)) {
                rejectUnexpectedFields(args, operation, Set.of("operation", "sessionId", "todoId"));
                requireLong(args, "todoId");
            }
            if ("delete".equalsIgnoreCase(operation)) {
                rejectUnexpectedFields(args, operation, Set.of("operation", "sessionId", "todoId"));
                requireLong(args, "todoId");
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid tool args", ex);
        }
    }

    @Override
    public ToolCallResult execute(ToolCallRequest request) {
        if (request == null) {
            return new ToolCallResult("FAILED", null, null, "TODO_CRUD_FAILED", "request must not be null");
        }
        try {
            validate(request);
            Map<String, Object> args = jsonCodec.readObject(request.toolArgsJson());
            String operation = stringValue(args.get("operation")).toLowerCase();
            Long sessionId = longValue(args.get("sessionId"));
            Long todoId = longValue(args.get("todoId"));
            SessionTodo candidate = buildCandidate(args);
            if ("create".equals(operation)) {
                SessionTodo created = todoCrudApplicationService.createTodo(
                        request.projectId(),
                        sessionId,
                        longValue(args.get("sourceRunId")) != null ? longValue(args.get("sourceRunId")) : request.runId(),
                        candidate,
                        request.operatorId(),
                        request.traceId()
                );
                return ToolCallResult.success(jsonCodec.write(buildTodoOutput("create", created)));
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
                return ToolCallResult.success(jsonCodec.write(output));
            }
            if ("update".equals(operation)) {
                Long sourceRunId = longValue(args.get("sourceRunId")) != null ? longValue(args.get("sourceRunId")) : request.runId();
                SessionTodo updated = todoCrudApplicationService.updateTodo(
                        request.projectId(),
                        sessionId,
                        todoId,
                        sourceRunId,
                        candidate,
                        request.operatorId(),
                        request.traceId()
                );
                return ToolCallResult.success(jsonCodec.write(buildTodoOutput("update", updated)));
            }
            if ("complete".equals(operation)) {
                SessionTodo completed = todoCrudApplicationService.completeTodo(
                        request.projectId(),
                        sessionId,
                        todoId,
                        request.operatorId(),
                        request.traceId()
                );
                return ToolCallResult.success(jsonCodec.write(buildTodoOutput("complete", completed)));
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
                return ToolCallResult.success(jsonCodec.write(output));
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
        output.put("sourceRunId", stringifyBusinessId(todo == null ? null : todo.getSourceRunId()));
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

    private void requireLong(Map<String, Object> args, String fieldName) {
        Long value = JsonValues.longValue(args, fieldName);
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        requireMinimumOne(value, fieldName);
    }

    private void validateOptionalLong(Map<String, Object> args, String fieldName) {
        Long value = JsonValues.longValue(args, fieldName);
        if (value == null) {
            return;
        }
        requireMinimumOne(value, fieldName);
    }

    private void requireNonBlank(Map<String, Object> args, String fieldName) {
        String value = JsonValues.nullableString(args, fieldName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private void requireMinimumOne(Long value, String fieldName) {
        if (value < 1) {
            throw new IllegalArgumentException(fieldName + " must be greater than or equal to 1");
        }
    }

    private void rejectUnexpectedFields(Map<String, Object> args, String operation, Set<String> allowedFields) {
        for (String fieldName : args.keySet()) {
            if (!allowedFields.contains(fieldName)) {
                throw new IllegalArgumentException("Unexpected field for operation " + operation + ": " + fieldName);
            }
        }
    }
}
