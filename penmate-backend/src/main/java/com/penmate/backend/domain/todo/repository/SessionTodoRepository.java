package com.penmate.backend.domain.todo.repository;

import com.penmate.backend.domain.todo.model.SessionTodo;

import java.util.List;

/**
 * 会话级 Todo 仓储。
 */
public interface SessionTodoRepository {

    List<SessionTodo> findBySession(Long projectId, Long sessionId, String status);

    SessionTodo findByTodoId(Long projectId, Long sessionId, Long todoId);

    void saveSessionTodos(Long projectId, Long sessionId, List<SessionTodo> todos);
}
