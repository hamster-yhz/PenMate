package com.penmate.backend.application.agent.tool.handler;

import cn.hutool.json.JSONObject;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.orchestration.AgentTaskRuntimeUpdater;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.todo.TodoCrudApplicationService;
import com.penmate.backend.domain.todo.model.SessionTodo;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        try {
            JSONObject args = AgentJsonCodec.parseObj(request.toolArgsJson());
            String operation = AgentJsonCodec.getString(args, "operation");
            if (operation == null || operation.isBlank()) {
                throw new IllegalArgumentException("operation is required");
            }
            Long sessionId = args.getLong("sessionId");
            if (sessionId == null) {
                throw new IllegalArgumentException("sessionId is required");
            }
            requireMinimumOne(sessionId, "sessionId");
            String normalizedOperation = operation.trim().toLowerCase();
            if (!Set.of("list", "get", "create", "update", "complete", "reorder", "delete").contains(normalizedOperation)) {
                throw new IllegalArgumentException("Unsupported operation: " + operation);
            }
            switch (normalizedOperation) {
                case "list" -> rejectUnexpectedFields(args, normalizedOperation, Set.of("operation", "sessionId", "status", "todoStatus"));
                case "get" -> {
                    rejectUnexpectedFields(args, normalizedOperation, Set.of("operation", "sessionId", "todoId"));
                    requireLong(args, "todoId");
                }
                case "create" -> {
                    rejectUnexpectedFields(args, normalizedOperation, Set.of("operation", "sessionId", "taskId", "title", "description", "status", "todoStatus", "priority", "orderIndex", "dependencies", "summary", "blockedReason", "errorSummary", "metadata"));
                    validateOptionalLong(args, "taskId");
                    requireNonBlank(args, "title");
                }
                case "update" -> {
                    rejectUnexpectedFields(args, normalizedOperation, Set.of("operation", "sessionId", "todoId", "taskId", "title", "description", "status", "todoStatus", "priority", "orderIndex", "dependencies", "summary", "blockedReason", "errorSummary", "metadata"));
                    requireLong(args, "todoId");
                    validateOptionalLong(args, "taskId");
                }
                case "complete" -> {
                    rejectUnexpectedFields(args, normalizedOperation, Set.of("operation", "sessionId", "todoId", "summary"));
                    requireLong(args, "todoId");
                }
                case "reorder" -> {
                    rejectUnexpectedFields(args, normalizedOperation, Set.of("operation", "sessionId", "orderedTodoIds"));
                    if (args.getJSONArray("orderedTodoIds") == null || args.getJSONArray("orderedTodoIds").isEmpty()) {
                        throw new IllegalArgumentException("orderedTodoIds is required");
                    }
                }
                case "delete" -> {
                    rejectUnexpectedFields(args, normalizedOperation, Set.of("operation", "sessionId", "todoId"));
                    requireLong(args, "todoId");
                }
                default -> throw new IllegalArgumentException("Unsupported operation: " + operation);
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
            Map<String, Object> args = OBJECT_MAPPER.readValue(request.toolArgsJson(), new TypeReference<>() {
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
            if ("get".equals(operation)) {
                SessionTodo todo = todoCrudApplicationService.getTodo(request.projectId(), sessionId, todoId);
                return ToolCallResult.success(AgentTaskRuntimeUpdater.toSnapshotJson(buildTodoOutput("get", todo)));
            }
            if ("list".equals(operation)) {
                String status = nullableStringValue(args.get("status"));
                if (status == null) {
                    status = nullableStringValue(args.get("todoStatus"));
                }
                java.util.List<SessionTodo> todos = todoCrudApplicationService.listSessionTodos(request.projectId(), sessionId, status);
                Map<String, Object> output = new LinkedHashMap<>();
                output.put("operation", "list");
                output.put("sessionId", stringifyBusinessId(sessionId));
                output.put("items", todos == null ? java.util.List.of() : todos.stream().map(todo -> buildTodoOutput(null, todo)).toList());
                return ToolCallResult.success(AgentTaskRuntimeUpdater.toSnapshotJson(output));
            }
            if ("update".equals(operation)) {
                Long taskId = longValue(args.get("taskId")) != null ? longValue(args.get("taskId")) : request.taskId();
                SessionTodo updated = todoCrudApplicationService.updateTodo(
                        request.projectId(), sessionId, todoId, taskId, candidate, request.operatorId(), request.traceId());
                return ToolCallResult.success(AgentTaskRuntimeUpdater.toSnapshotJson(buildTodoOutput("update", updated)));
            }
            if ("complete".equals(operation)) {
                SessionTodo completion = new SessionTodo();
                completion.setStatus("completed");
                completion.setSummary(nullableStringValue(args.get("summary")) == null ? "completed" : nullableStringValue(args.get("summary")));
                SessionTodo completed = todoCrudApplicationService.updateTodo(
                        request.projectId(), sessionId, todoId, request.taskId(), completion, request.operatorId(), request.traceId());
                return ToolCallResult.success(AgentTaskRuntimeUpdater.toSnapshotJson(buildTodoOutput("complete", completed)));
            }
            if ("reorder".equals(operation)) {
                List<Long> orderedTodoIds = ((List<?>) args.get("orderedTodoIds")).stream().map(this::longValue).toList();
                java.util.List<SessionTodo> todos = todoCrudApplicationService.reorderTodos(
                        request.projectId(), sessionId, orderedTodoIds, request.operatorId(), request.traceId());
                Map<String, Object> output = new LinkedHashMap<>();
                output.put("operation", "reorder");
                output.put("sessionId", stringifyBusinessId(sessionId));
                output.put("items", todos.stream().map(todo -> buildTodoOutput(null, todo)).toList());
                return ToolCallResult.success(AgentTaskRuntimeUpdater.toSnapshotJson(output));
            }
            if ("delete".equals(operation)) {
                todoCrudApplicationService.deleteTodo(request.projectId(), sessionId, todoId, request.operatorId(), request.traceId());
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
        candidate.setTitle(nullableStringValue(args.get("title")));
        candidate.setDescription(nullableStringValue(args.get("description")));
        candidate.setStatus(nullableStringValue(args.get("status")));
        candidate.setTodoStatus(nullableStringValue(args.get("todoStatus")));
        candidate.setPriority(integerValue(args.get("priority")));
        candidate.setOrderIndex(integerValue(args.get("orderIndex")));
        candidate.setSummary(nullableStringValue(args.get("summary")));
        candidate.setBlockedReason(nullableStringValue(args.get("blockedReason")));
        candidate.setErrorSummary(nullableStringValue(args.get("errorSummary")));
        candidate.setMetadata(nullableStringValue(args.get("metadata")));
        Object dependencies = args.get("dependencies");
        if (dependencies instanceof List<?> list) {
            candidate.setDependencies(list.stream().map(String::valueOf).toList());
        }
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
        output.put("status", todo == null ? null : todo.getStatus());
        output.put("priority", todo == null ? null : todo.getPriority());
        output.put("orderIndex", todo == null ? null : todo.getOrderIndex());
        output.put("dependencies", todo == null ? null : todo.getDependencies());
        output.put("summary", todo == null ? null : todo.getSummary());
        output.put("blockedReason", todo == null ? null : todo.getBlockedReason());
        output.put("errorSummary", todo == null ? null : todo.getErrorSummary());
        output.put("metadata", todo == null ? null : todo.getMetadata());
        output.put("completedAt", todo == null || todo.getCompletedAt() == null ? null : todo.getCompletedAt().toString());
        output.put("createdAt", todo == null || todo.getCreatedAt() == null ? null : todo.getCreatedAt().toString());
        output.put("updatedAt", todo == null || todo.getUpdatedAt() == null ? null : todo.getUpdatedAt().toString());
        return output;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String nullableStringValue(Object value) {
        String normalized = value == null ? null : String.valueOf(value).trim();
        return normalized == null || normalized.isBlank() ? null : normalized;
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

    private Integer integerValue(Object value) {
        Long longValue = longValue(value);
        return longValue == null ? null : longValue.intValue();
    }

    private String stringifyBusinessId(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private void requireLong(JSONObject args, String fieldName) {
        Long value = args.getLong(fieldName);
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        requireMinimumOne(value, fieldName);
    }

    private void validateOptionalLong(JSONObject args, String fieldName) {
        Long value = args.getLong(fieldName);
        if (value != null) {
            requireMinimumOne(value, fieldName);
        }
    }

    private void requireNonBlank(JSONObject args, String fieldName) {
        String value = args.getStr(fieldName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private void requireMinimumOne(Long value, String fieldName) {
        if (value < 1) {
            throw new IllegalArgumentException(fieldName + " must be greater than or equal to 1");
        }
    }

    private void rejectUnexpectedFields(JSONObject args, String operation, Set<String> allowedFields) {
        for (String fieldName : args.keySet()) {
            if (!allowedFields.contains(fieldName)) {
                throw new IllegalArgumentException("Unexpected field for operation " + operation + ": " + fieldName);
            }
        }
    }
}
