package com.penmate.backend.interfaces.api.todo;

import com.penmate.backend.application.todo.TodoCrudApplicationService;
import com.penmate.backend.domain.todo.model.SessionTodo;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import com.penmate.backend.interfaces.api.todo.dto.CreateTodoDto;
import com.penmate.backend.interfaces.api.todo.dto.UpdateTodoDto;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/novels/{projectId}/agent/sessions/{sessionId}/todos")
@Slf4j
public class TodoController {

    private final TodoCrudApplicationService todoCrudApplicationService;

    public TodoController(TodoCrudApplicationService todoCrudApplicationService) {
        this.todoCrudApplicationService = todoCrudApplicationService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listTodos(@PathVariable String projectId,
                                                            @PathVariable String sessionId,
                                                            @RequestParam(value = "status", required = false) String status,
                                                            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(todoCrudApplicationService.listSessionTodos(
                requireLongId(projectId, "projectId"),
                requireLongId(sessionId, "sessionId"),
                status
        ).stream().map(this::toView).toList(), traceId);
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createTodo(@PathVariable String projectId,
                                                       @PathVariable String sessionId,
                                                       @Valid @RequestBody CreateTodoDto dto,
                                                       @RequestParam("operatorId") String operatorId,
                                                       @RequestParam(value = "taskId", required = false) String taskId,
                                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        SessionTodo candidate = toSessionTodo(dto);
        SessionTodo created = todoCrudApplicationService.createTodo(
                requireLongId(projectId, "projectId"),
                requireLongId(sessionId, "sessionId"),
                optionalLongId(taskId, "taskId"),
                candidate,
                requireLongId(operatorId, "operatorId"),
                traceId
        );
        return ApiResponse.success(toView(created), traceId);
    }

    @PostMapping("/batch")
    public ApiResponse<List<Map<String, Object>>> batchCreateTodos(@PathVariable String projectId,
                                                                   @PathVariable String sessionId,
                                                                   @Valid @RequestBody List<CreateTodoDto> dtos,
                                                                   @RequestParam("operatorId") String operatorId,
                                                                   @RequestParam(value = "taskId", required = false) String taskId,
                                                                   @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        List<SessionTodo> todos = dtos == null ? List.of() : dtos.stream().map(this::toSessionTodo).toList();
        return ApiResponse.success(todoCrudApplicationService.batchCreateTodos(
                requireLongId(projectId, "projectId"),
                requireLongId(sessionId, "sessionId"),
                optionalLongId(taskId, "taskId"),
                todos,
                requireLongId(operatorId, "operatorId"),
                traceId
        ).stream().map(this::toView).toList(), traceId);
    }

    @PutMapping("/{todoId}")
    public ApiResponse<Map<String, Object>> updateTodo(@PathVariable String projectId,
                                                       @PathVariable String sessionId,
                                                       @PathVariable String todoId,
                                                       @Valid @RequestBody UpdateTodoDto dto,
                                                       @RequestParam("operatorId") String operatorId,
                                                       @RequestParam(value = "taskId", required = false) String taskId,
                                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        SessionTodo candidate = toSessionTodo(dto);
        SessionTodo updated = todoCrudApplicationService.updateTodo(
                requireLongId(projectId, "projectId"),
                requireLongId(sessionId, "sessionId"),
                requireLongId(todoId, "todoId"),
                optionalLongId(taskId, "taskId"),
                candidate,
                requireLongId(operatorId, "operatorId"),
                traceId
        );
        return ApiResponse.success(toView(updated), traceId);
    }

    @PostMapping("/{todoId}/complete")
    public ApiResponse<Map<String, Object>> completeTodo(@PathVariable String projectId,
                                                         @PathVariable String sessionId,
                                                         @PathVariable String todoId,
                                                         @RequestParam("operatorId") String operatorId,
                                                         @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        SessionTodo completed = todoCrudApplicationService.completeTodo(
                requireLongId(projectId, "projectId"),
                requireLongId(sessionId, "sessionId"),
                requireLongId(todoId, "todoId"),
                requireLongId(operatorId, "operatorId"),
                traceId
        );
        return ApiResponse.success(toView(completed), traceId);
    }

    @DeleteMapping("/{todoId}")
    public ApiResponse<String> deleteTodo(@PathVariable String projectId,
                                          @PathVariable String sessionId,
                                          @PathVariable String todoId,
                                          @RequestParam("operatorId") String operatorId,
                                          @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        todoCrudApplicationService.deleteTodo(
                requireLongId(projectId, "projectId"),
                requireLongId(sessionId, "sessionId"),
                requireLongId(todoId, "todoId"),
                requireLongId(operatorId, "operatorId"),
                traceId
        );
        return ApiResponse.success("deleted", traceId);
    }

    private SessionTodo toSessionTodo(CreateTodoDto dto) {
        SessionTodo sessionTodo = new SessionTodo();
        sessionTodo.setTitle(dto == null ? null : dto.getTitle());
        sessionTodo.setDescription(dto == null ? null : dto.getDescription());
        sessionTodo.setSourceType(dto == null ? null : dto.getSourceType());
        sessionTodo.setTodoStatus(dto == null ? null : dto.getTodoStatus());
        return sessionTodo;
    }

    private SessionTodo toSessionTodo(UpdateTodoDto dto) {
        SessionTodo sessionTodo = new SessionTodo();
        sessionTodo.setTitle(dto == null ? null : dto.getTitle());
        sessionTodo.setDescription(dto == null ? null : dto.getDescription());
        sessionTodo.setSourceType(dto == null ? null : dto.getSourceType());
        sessionTodo.setTodoStatus(dto == null ? null : dto.getTodoStatus());
        return sessionTodo;
    }

    private Map<String, Object> toView(SessionTodo todo) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todoId", stringifyBusinessId(todo == null ? null : todo.getTodoId()));
        data.put("projectId", stringifyBusinessId(todo == null ? null : todo.getProjectId()));
        data.put("sessionId", stringifyBusinessId(todo == null ? null : todo.getSessionId()));
        data.put("taskId", stringifyBusinessId(todo == null ? null : todo.getTaskId()));
        data.put("title", todo == null ? null : todo.getTitle());
        data.put("description", todo == null ? null : todo.getDescription());
        data.put("sourceType", todo == null ? null : todo.getSourceType());
        data.put("todoStatus", todo == null ? null : todo.getTodoStatus());
        data.put("completedAt", todo == null ? null : todo.getCompletedAt());
        data.put("createdAt", todo == null ? null : todo.getCreatedAt());
        data.put("updatedAt", todo == null ? null : todo.getUpdatedAt());
        return data;
    }

    private Long requireLongId(String rawValue, String fieldName) {
        String normalized = Objects.requireNonNull(rawValue, fieldName + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (!normalized.matches("^\\d+$")) {
            throw new IllegalArgumentException(fieldName + " must be a numeric string business id");
        }
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a valid numeric string business id", ex);
        }
    }

    private Long optionalLongId(String rawValue, String fieldName) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return requireLongId(rawValue, fieldName);
    }

    private String stringifyBusinessId(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}
