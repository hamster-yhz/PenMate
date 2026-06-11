package com.penmate.backend.application.todo;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.todo.model.SessionTodo;
import com.penmate.backend.domain.todo.repository.SessionTodoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
public class TodoCrudApplicationService {

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "pending", "in_progress", "completed", "blocked", "failed", "cancelled"
    );

    private final SessionTodoRepository sessionTodoRepository;
    private final BusinessIdGenerator businessIdGenerator;

    public TodoCrudApplicationService(SessionTodoRepository sessionTodoRepository,
                                      BusinessIdGenerator businessIdGenerator) {
        this.sessionTodoRepository = sessionTodoRepository;
        this.businessIdGenerator = businessIdGenerator;
    }

    public List<SessionTodo> listSessionTodos(Long projectId, Long sessionId, String status) {
        return sessionTodoRepository.findBySession(projectId, sessionId, normalizeNullable(status));
    }

    public SessionTodo getTodo(Long projectId, Long sessionId, Long todoId) {
        return requireTodo(projectId, sessionId, todoId);
    }

    @Transactional
    public SessionTodo createTodo(Long projectId,
                                  Long sessionId,
                                  Long taskId,
                                  SessionTodo candidate,
                                  Long operatorId,
                                  String traceId) {
        List<SessionTodo> todos = mutableTodos(projectId, sessionId);
        SessionTodo todo = new SessionTodo();
        todo.setTodoId(businessIdGenerator.nextId());
        todo.setProjectId(projectId);
        todo.setSessionId(sessionId);
        todo.setTaskId(taskId);
        todo.setTitle(requiredTitle(candidate));
        todo.setDescription(normalizeNullable(candidate == null ? null : candidate.getDescription()));
        todo.setStatus(normalizeStatus(candidate == null ? null : candidate.getStatus(), candidate == null ? null : candidate.getTodoStatus()));
        todo.setTodoStatus(todo.getStatus());
        todo.setPriority(candidate == null || candidate.getPriority() == null ? 100 : candidate.getPriority());
        todo.setOrderIndex(candidate == null || candidate.getOrderIndex() == null ? nextOrderIndex(todos) : candidate.getOrderIndex());
        todo.setDependencies(candidate == null || candidate.getDependencies() == null ? List.of() : List.copyOf(candidate.getDependencies()));
        todo.setSummary(normalizeNullable(candidate == null ? null : candidate.getSummary()));
        todo.setBlockedReason(normalizeNullable(candidate == null ? null : candidate.getBlockedReason()));
        todo.setErrorSummary(normalizeNullable(candidate == null ? null : candidate.getErrorSummary()));
        todo.setMetadata(normalizeNullable(candidate == null ? null : candidate.getMetadata()));
        LocalDateTime now = LocalDateTime.now();
        todo.setCreatedAt(now);
        todo.setUpdatedAt(now);
        applyTerminalTimestamps(todo, now);
        validateTodo(todo, todos, null);
        todos.add(todo);
        save(projectId, sessionId, todos);
        log.info("created session todo: projectId={}, sessionId={}, todoId={}, status={}, operatorId={}, traceId={}",
                projectId, sessionId, todo.getTodoId(), todo.getStatus(), operatorId, traceId);
        return todo;
    }

    @Transactional
    public List<SessionTodo> batchCreateTodos(Long projectId,
                                              Long sessionId,
                                              Long taskId,
                                              List<SessionTodo> todos,
                                              Long operatorId,
                                              String traceId) {
        List<SessionTodo> created = new ArrayList<>();
        for (SessionTodo todo : todos == null ? List.<SessionTodo>of() : todos) {
            created.add(createTodo(projectId, sessionId, taskId, todo, operatorId, traceId));
        }
        return created;
    }

    @Transactional
    public SessionTodo updateTodo(Long projectId,
                                  Long sessionId,
                                  Long todoId,
                                  Long taskId,
                                  SessionTodo candidate,
                                  Long operatorId,
                                  String traceId) {
        List<SessionTodo> todos = mutableTodos(projectId, sessionId);
        SessionTodo existing = todos.stream()
                .filter(todo -> Objects.equals(todo.getTodoId(), todoId))
                .findFirst()
                .orElseThrow(() -> BusinessException.notFound("Todo not found"));
        existing.setTaskId(taskId == null ? existing.getTaskId() : taskId);
        if (candidate != null && candidate.getTitle() != null) {
            existing.setTitle(requiredTitle(candidate));
        }
        if (candidate != null && candidate.getDescription() != null) {
            existing.setDescription(normalizeNullable(candidate.getDescription()));
        }
        if (candidate != null && (candidate.getStatus() != null || candidate.getTodoStatus() != null)) {
            existing.setStatus(normalizeStatus(candidate.getStatus(), candidate.getTodoStatus()));
            existing.setTodoStatus(existing.getStatus());
        }
        if (candidate != null && candidate.getPriority() != null) {
            existing.setPriority(candidate.getPriority());
        }
        if (candidate != null && candidate.getOrderIndex() != null) {
            existing.setOrderIndex(candidate.getOrderIndex());
        }
        if (candidate != null && candidate.getDependencies() != null) {
            existing.setDependencies(List.copyOf(candidate.getDependencies()));
        }
        if (candidate != null && candidate.getSummary() != null) {
            existing.setSummary(normalizeNullable(candidate.getSummary()));
        }
        if (candidate != null && candidate.getBlockedReason() != null) {
            existing.setBlockedReason(normalizeNullable(candidate.getBlockedReason()));
        }
        if (candidate != null && candidate.getErrorSummary() != null) {
            existing.setErrorSummary(normalizeNullable(candidate.getErrorSummary()));
        }
        if (candidate != null && candidate.getMetadata() != null) {
            existing.setMetadata(normalizeNullable(candidate.getMetadata()));
        }
        LocalDateTime now = LocalDateTime.now();
        existing.setUpdatedAt(now);
        applyTerminalTimestamps(existing, now);
        validateTodo(existing, todos, todoId);
        save(projectId, sessionId, todos);
        log.info("updated session todo: projectId={}, sessionId={}, todoId={}, status={}, operatorId={}, traceId={}",
                projectId, sessionId, todoId, existing.getStatus(), operatorId, traceId);
        return existing;
    }

    public SessionTodo completeTodo(Long projectId,
                                    Long sessionId,
                                    Long todoId,
                                    Long operatorId,
                                    String traceId) {
        SessionTodo candidate = new SessionTodo();
        candidate.setStatus("completed");
        candidate.setSummary("completed");
        return updateTodo(projectId, sessionId, todoId, null, candidate, operatorId, traceId);
    }

    @Transactional
    public void deleteTodo(Long projectId,
                           Long sessionId,
                           Long todoId,
                           Long operatorId,
                           String traceId) {
        List<SessionTodo> todos = mutableTodos(projectId, sessionId);
        boolean removed = todos.removeIf(todo -> Objects.equals(todo.getTodoId(), todoId));
        if (!removed) {
            throw BusinessException.notFound("Todo not found");
        }
        save(projectId, sessionId, todos);
        log.info("deleted session todo: projectId={}, sessionId={}, todoId={}, operatorId={}, traceId={}",
                projectId, sessionId, todoId, operatorId, traceId);
    }

    @Transactional
    public List<SessionTodo> reorderTodos(Long projectId,
                                          Long sessionId,
                                          List<Long> orderedTodoIds,
                                          Long operatorId,
                                          String traceId) {
        List<SessionTodo> todos = mutableTodos(projectId, sessionId);
        if (orderedTodoIds == null || orderedTodoIds.isEmpty()) {
            throw BusinessException.badRequest("orderedTodoIds must not be empty");
        }
        for (int i = 0; i < orderedTodoIds.size(); i++) {
            Long todoId = orderedTodoIds.get(i);
            SessionTodo todo = todos.stream()
                    .filter(item -> Objects.equals(item.getTodoId(), todoId))
                    .findFirst()
                    .orElseThrow(() -> BusinessException.notFound("Todo not found"));
            todo.setOrderIndex(i + 1);
            todo.setUpdatedAt(LocalDateTime.now());
        }
        save(projectId, sessionId, todos);
        return listSessionTodos(projectId, sessionId, null);
    }

    private List<SessionTodo> mutableTodos(Long projectId, Long sessionId) {
        return new ArrayList<>(sessionTodoRepository.findBySession(projectId, sessionId, null));
    }

    private void save(Long projectId, Long sessionId, List<SessionTodo> todos) {
        sessionTodoRepository.saveSessionTodos(projectId, sessionId, todos.stream()
                .sorted(Comparator.comparing(SessionTodo::getOrderIndex, Comparator.nullsLast(Integer::compareTo)))
                .toList());
    }

    private SessionTodo requireTodo(Long projectId, Long sessionId, Long todoId) {
        SessionTodo sessionTodo = sessionTodoRepository.findByTodoId(projectId, sessionId, todoId);
        if (sessionTodo == null) {
            throw BusinessException.notFound("Todo not found");
        }
        return sessionTodo;
    }

    private String requiredTitle(SessionTodo candidate) {
        String title = normalizeNullable(candidate == null ? null : candidate.getTitle());
        if (title == null || title.isBlank()) {
            throw BusinessException.badRequest("todo title must not be blank");
        }
        return title;
    }

    private String normalizeStatus(String status, String legacyStatus) {
        String raw = status == null || status.isBlank() ? legacyStatus : status;
        String normalized = normalizeNullable(raw);
        if (normalized == null || normalized.isBlank()) {
            return "pending";
        }
        return switch (normalized.trim().toLowerCase()) {
            case "todo" -> "pending";
            case "done" -> "completed";
            default -> normalized.trim().toLowerCase();
        };
    }

    private void validateTodo(SessionTodo todo, List<SessionTodo> sessionTodos, Long updatingTodoId) {
        if (!ALLOWED_STATUSES.contains(todo.getStatus())) {
            throw BusinessException.badRequest("status must be one of " + ALLOWED_STATUSES);
        }
        if ("blocked".equals(todo.getStatus()) && isBlank(todo.getBlockedReason())) {
            throw BusinessException.badRequest("blockedReason is required when status is blocked");
        }
        if ("failed".equals(todo.getStatus()) && isBlank(todo.getErrorSummary())) {
            throw BusinessException.badRequest("errorSummary is required when status is failed");
        }
        if ("completed".equals(todo.getStatus()) && isBlank(todo.getSummary())) {
            throw BusinessException.badRequest("summary is required when status is completed");
        }
        if ("in_progress".equals(todo.getStatus())) {
            boolean anotherInProgress = sessionTodos.stream()
                    .filter(item -> !Objects.equals(item.getTodoId(), updatingTodoId))
                    .anyMatch(item -> "in_progress".equals(item.getStatus()));
            if (anotherInProgress) {
                throw BusinessException.conflict("only one in_progress todo is allowed per session");
            }
        }
    }

    private void applyTerminalTimestamps(SessionTodo todo, LocalDateTime now) {
        if ("completed".equals(todo.getStatus())) {
            todo.setCompletedAt(now);
        } else {
            todo.setCompletedAt(null);
        }
    }

    private int nextOrderIndex(List<SessionTodo> todos) {
        return todos.stream()
                .map(SessionTodo::getOrderIndex)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private String normalizeNullable(String value) {
        return value == null ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
