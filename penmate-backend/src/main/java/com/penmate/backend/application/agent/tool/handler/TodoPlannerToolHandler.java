package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext;
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

/** Lets the primary agent read and mutate the current session's persisted todo plan. */
@Component
@RequiredArgsConstructor
public class TodoPlannerToolHandler implements AgentToolHandler {

    private static final Set<String> OPERATIONS = Set.of("list", "create", "update", "complete", "delete");
    private final TodoCrudApplicationService todoService;
    private final JsonCodec jsonCodec;

    @Override
    public String toolCode() {
        return "todo_planner";
    }

    @Override
    public boolean mutatesState(AuthorizedAgentRunContext context, ToolCallRequest request) {
        return !"list".equalsIgnoreCase(operation(request));
    }

    @Override
    public void validate(AuthorizedAgentRunContext context, ToolCallRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (context.sessionId() < 1) {
            throw new IllegalArgumentException("current session is required");
        }
        Map<String, Object> args = readArgs(request);
        String operation = JsonValues.string(args, "operation").toLowerCase();
        if (!OPERATIONS.contains(operation)) {
            throw new IllegalArgumentException("operation must be one of " + OPERATIONS);
        }
        switch (operation) {
            case "list" -> rejectUnexpectedFields(args, operation, Set.of("operation", "todoStatus"));
            case "create" -> {
                rejectUnexpectedFields(args, operation,
                        Set.of("operation", "title", "description", "sourceType", "todoStatus"));
                requireNonBlank(args, "title");
                requireNonBlank(args, "sourceType");
                requireNonBlank(args, "todoStatus");
            }
            case "update" -> {
                rejectUnexpectedFields(args, operation,
                        Set.of("operation", "todoId", "title", "description", "sourceType", "todoStatus"));
                requirePositiveLong(args, "todoId");
                requireNonBlank(args, "title");
                requireNonBlank(args, "sourceType");
                requireNonBlank(args, "todoStatus");
            }
            case "complete", "delete" -> {
                rejectUnexpectedFields(args, operation, Set.of("operation", "todoId"));
                requirePositiveLong(args, "todoId");
            }
            default -> throw new IllegalArgumentException("unsupported operation: " + operation);
        }
    }

    @Override
    public ToolCallResult execute(AuthorizedAgentRunContext context, ToolCallRequest request) {
        try {
            validate(context, request);
            Map<String, Object> args = readArgs(request);
            String operation = JsonValues.string(args, "operation").toLowerCase();
            Long todoId = JsonValues.longValue(args, "todoId");
            Long sessionId = context.sessionId();
            SessionTodo todo;
            return switch (operation) {
                case "list" -> list(context, args, sessionId);
                case "create" -> {
                    todo = todoService.createTodo(context.projectId(), sessionId, context.runId(), candidate(args),
                            context.ownerUserId(), context.traceId());
                    yield success(operation, todo);
                }
                case "update" -> {
                    todo = todoService.updateTodo(context.projectId(), sessionId, todoId, context.runId(), candidate(args),
                            context.ownerUserId(), context.traceId());
                    yield success(operation, todo);
                }
                case "complete" -> {
                    todo = todoService.completeTodo(context.projectId(), sessionId, todoId,
                            context.ownerUserId(), context.traceId());
                    yield success(operation, todo);
                }
                case "delete" -> {
                    todoService.deleteTodo(context.projectId(), sessionId, todoId,
                            context.ownerUserId(), context.traceId());
                    yield ToolCallResult.success(jsonCodec.write(Map.of(
                            "operation", "delete",
                            "todoId", String.valueOf(todoId),
                            "deleted", true
                    )));
                }
                default -> throw new IllegalArgumentException("unsupported operation: " + operation);
            };
        } catch (Exception ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "todo planner execution failed" : ex.getMessage();
            return new ToolCallResult("FAILED", null, null, "TODO_PLANNER_FAILED", message);
        }
    }

    private ToolCallResult list(AuthorizedAgentRunContext context, Map<String, Object> args, Long sessionId) {
        var todos = todoService.listSessionTodos(context.projectId(), sessionId,
                JsonValues.nullableString(args, "todoStatus"));
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("operation", "list");
        output.put("items", todos.stream().map(this::todoOutput).toList());
        return ToolCallResult.success(jsonCodec.write(output));
    }

    private ToolCallResult success(String operation, SessionTodo todo) {
        Map<String, Object> output = todoOutput(todo);
        output.put("operation", operation);
        return ToolCallResult.success(jsonCodec.write(output));
    }

    private SessionTodo candidate(Map<String, Object> args) {
        SessionTodo todo = new SessionTodo();
        todo.setTitle(JsonValues.string(args, "title"));
        todo.setDescription(JsonValues.nullableString(args, "description"));
        todo.setSourceType(JsonValues.string(args, "sourceType"));
        todo.setTodoStatus(JsonValues.string(args, "todoStatus"));
        return todo;
    }

    private Map<String, Object> todoOutput(SessionTodo todo) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("todoId", id(todo == null ? null : todo.getTodoId()));
        output.put("title", todo == null ? null : todo.getTitle());
        output.put("description", todo == null ? null : todo.getDescription());
        output.put("sourceType", todo == null ? null : todo.getSourceType());
        output.put("todoStatus", todo == null ? null : todo.getTodoStatus());
        output.put("completedAt", todo == null || todo.getCompletedAt() == null ? null : todo.getCompletedAt().toString());
        return output;
    }

    private Map<String, Object> readArgs(ToolCallRequest request) {
        try {
            return jsonCodec.readObject(request.toolArgsJson());
        } catch (Exception ex) {
            throw new IllegalArgumentException("toolArgsJson must be valid JSON", ex);
        }
    }

    private String operation(ToolCallRequest request) {
        return request == null ? "" : JsonValues.string(readArgs(request), "operation");
    }

    private void requireNonBlank(Map<String, Object> args, String field) {
        if (JsonValues.string(args, field).isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private void requirePositiveLong(Map<String, Object> args, String field) {
        Long value = JsonValues.longValue(args, field);
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (value < 1) {
            throw new IllegalArgumentException(field + " must be greater than or equal to 1");
        }
    }

    private void rejectUnexpectedFields(Map<String, Object> args, String operation, Set<String> allowed) {
        args.keySet().stream().filter(field -> !allowed.contains(field)).findFirst().ifPresent(field -> {
            throw new IllegalArgumentException("Unexpected field for operation " + operation + ": " + field);
        });
    }

    private String id(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}
