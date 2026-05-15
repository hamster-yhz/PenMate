package com.penmate.backend.domain.todo.repository;

import com.penmate.backend.domain.todo.model.SessionTodo;

import java.util.List;

/**
 * 会话级 Todo 仓储。
 */
public interface SessionTodoRepository {

    List<SessionTodo> findBySession(Long projectId, Long sessionId, String todoStatus);

    SessionTodo findByTodoId(Long projectId, Long sessionId, Long todoId);

    int insert(SessionTodo sessionTodo);

    int update(SessionTodo sessionTodo);

    int markCompleted(Long projectId, Long sessionId, Long todoId);

    int softDelete(Long projectId, Long sessionId, Long todoId);
}
