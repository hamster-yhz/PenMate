package com.penmate.backend.infrastructure.persistence.todo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.todo.model.SessionTodo;
import com.penmate.backend.domain.todo.repository.SessionTodoRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Repository
public class SessionTodoRepositoryImpl implements SessionTodoRepository {

    private static final Duration TODO_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public SessionTodoRepositoryImpl(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public List<SessionTodo> findBySession(Long projectId, Long sessionId, String status) {
        SessionTodoDocument document = readDocument(projectId, sessionId);
        if (document == null || document.items() == null) {
            return List.of();
        }
        return document.items().stream()
                .filter(todo -> status == null || status.isBlank() || status.equalsIgnoreCase(todo.getStatus()))
                .map(this::copy)
                .toList();
    }

    @Override
    public SessionTodo findByTodoId(Long projectId, Long sessionId, Long todoId) {
        return findBySession(projectId, sessionId, null).stream()
                .filter(todo -> Objects.equals(todo.getTodoId(), todoId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void saveSessionTodos(Long projectId, Long sessionId, List<SessionTodo> todos) {
        SessionTodoDocument document = new SessionTodoDocument(
                projectId,
                sessionId,
                todos == null ? List.of() : todos.stream().map(this::copy).toList()
        );
        try {
            stringRedisTemplate.opsForValue().set(key(projectId, sessionId), objectMapper.writeValueAsString(document), TODO_TTL);
        } catch (JsonProcessingException ex) {
            throw BusinessException.of("todo persistence failed");
        }
    }

    private SessionTodoDocument readDocument(Long projectId, Long sessionId) {
        String key = key(projectId, sessionId);
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return null;
        }
        stringRedisTemplate.expire(key, TODO_TTL);
        try {
            return objectMapper.readValue(json, SessionTodoDocument.class);
        } catch (JsonProcessingException ex) {
            throw BusinessException.of("todo persistence failed");
        }
    }

    private String key(Long projectId, Long sessionId) {
        return "agent:session:" + projectId + ":" + sessionId + ":todo";
    }

    private SessionTodo copy(SessionTodo source) {
        if (source == null) {
            return null;
        }
        SessionTodo copy = new SessionTodo();
        copy.setId(source.getId());
        copy.setTodoId(source.getTodoId());
        copy.setProjectId(source.getProjectId());
        copy.setSessionId(source.getSessionId());
        copy.setTaskId(source.getTaskId());
        copy.setTitle(source.getTitle());
        copy.setDescription(source.getDescription());
        copy.setSourceType(source.getSourceType());
        copy.setTodoStatus(source.getTodoStatus());
        copy.setStatus(source.getStatus());
        copy.setPriority(source.getPriority());
        copy.setOrderIndex(source.getOrderIndex());
        copy.setDependencies(source.getDependencies() == null ? new ArrayList<>() : new ArrayList<>(source.getDependencies()));
        copy.setSummary(source.getSummary());
        copy.setBlockedReason(source.getBlockedReason());
        copy.setErrorSummary(source.getErrorSummary());
        copy.setMetadata(source.getMetadata());
        copy.setCompletedAt(source.getCompletedAt());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setDeletedAt(source.getDeletedAt());
        return copy;
    }

    private record SessionTodoDocument(Long projectId, Long sessionId, List<SessionTodo> items) {
    }
}
