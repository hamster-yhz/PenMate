package com.penmate.backend.infrastructure.persistence.todo;

import com.penmate.backend.domain.todo.model.SessionTodo;
import com.penmate.backend.domain.todo.repository.SessionTodoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 会话级 Todo 仓储 MyBatis 实现。
 */
@Repository
public class SessionTodoRepositoryImpl implements SessionTodoRepository {

    private final SessionTodoMapper sessionTodoMapper;

    public SessionTodoRepositoryImpl(SessionTodoMapper sessionTodoMapper) {
        this.sessionTodoMapper = sessionTodoMapper;
    }

    @Override
    public List<SessionTodo> findBySession(Long projectId, Long sessionId, String todoStatus) {
        if (todoStatus == null || todoStatus.isBlank()) {
            return sessionTodoMapper.findBySession(projectId, sessionId);
        }
        return sessionTodoMapper.findBySessionAndStatus(projectId, sessionId, todoStatus);
    }

    @Override
    public SessionTodo findByTodoId(Long projectId, Long sessionId, Long todoId) {
        return sessionTodoMapper.findByTodoId(projectId, sessionId, todoId);
    }

    @Override
    public int insert(SessionTodo sessionTodo) {
        return sessionTodoMapper.insert(sessionTodo);
    }

    @Override
    public int update(SessionTodo sessionTodo) {
        return sessionTodoMapper.update(sessionTodo);
    }

    @Override
    public int markCompleted(Long projectId, Long sessionId, Long todoId) {
        return sessionTodoMapper.markCompleted(projectId, sessionId, todoId);
    }

    @Override
    public int softDelete(Long projectId, Long sessionId, Long todoId) {
        return sessionTodoMapper.softDelete(projectId, sessionId, todoId);
    }
}
