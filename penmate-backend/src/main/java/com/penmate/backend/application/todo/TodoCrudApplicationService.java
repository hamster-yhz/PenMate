package com.penmate.backend.application.todo;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.todo.model.SessionTodo;
import com.penmate.backend.domain.todo.repository.SessionTodoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话级 Todo CRUD 应用服务。
 */
@Service
@Slf4j
public class TodoCrudApplicationService {

    private static final Set<String> ALLOWED_SOURCE_TYPES = Set.of("USER_REQUEST", "QUALITY_REVIEW", "STORY_BIBLE_UPDATE", "PLANNING");
    private static final Set<String> ALLOWED_TODO_STATUSES = Set.of("TODO", "IN_PROGRESS", "BLOCKED", "DONE");

    private final SessionTodoRepository sessionTodoRepository;
    private final BusinessIdGenerator businessIdGenerator;

    public TodoCrudApplicationService(SessionTodoRepository sessionTodoRepository,
                                      BusinessIdGenerator businessIdGenerator) {
        this.sessionTodoRepository = sessionTodoRepository;
        this.businessIdGenerator = businessIdGenerator;
    }

    public List<SessionTodo> listSessionTodos(Long projectId, Long sessionId, String todoStatus) {
        List<SessionTodo> todos = sessionTodoRepository.findBySession(projectId, sessionId, todoStatus);
        log.info("查询会话待办: projectId={}, sessionId={}, todoStatus={}, count={}",
                projectId, sessionId, todoStatus, todos.size());
        return todos;
    }

    public SessionTodo createTodo(Long projectId,
                                  Long sessionId,
                                  Long sourceRunId,
                                  SessionTodo candidate,
                                  Long operatorId,
                                  String traceId) {
        SessionTodo sessionTodo = new SessionTodo();
        sessionTodo.setTodoId(businessIdGenerator.nextId());
        sessionTodo.setProjectId(projectId);
        sessionTodo.setSessionId(sessionId);
        sessionTodo.setSourceRunId(sourceRunId);
        sessionTodo.setTitle(normalize(candidate == null ? null : candidate.getTitle()));
        sessionTodo.setDescription(normalizeNullable(candidate == null ? null : candidate.getDescription()));
        sessionTodo.setSourceType(normalize(candidate == null ? null : candidate.getSourceType()));
        sessionTodo.setTodoStatus(normalize(candidate == null ? null : candidate.getTodoStatus()));
        validateTodo(sessionTodo);
        if ("DONE".equals(sessionTodo.getTodoStatus())) {
            sessionTodo.setCompletedAt(Instant.now());
        }
        try {
            int affected = sessionTodoRepository.insert(sessionTodo);
            if (affected != 1) {
                log.error("创建会话待办失败: projectId={}, sessionId={}, sourceRunId={}, operatorId={}, traceId={}, reason=insert_failed",
                        projectId, sessionId, sourceRunId, operatorId, traceId);
                throw BusinessException.of("todo persistence failed");
            }
            log.info("创建会话待办成功: projectId={}, sessionId={}, todoId={}, sourceRunId={}, sourceType={}, todoStatus={}, operatorId={}, traceId={}",
                    projectId, sessionId, sessionTodo.getTodoId(), sourceRunId, sessionTodo.getSourceType(), sessionTodo.getTodoStatus(), operatorId, traceId);
            return sessionTodo;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("创建会话待办异常: projectId={}, sessionId={}, sourceRunId={}, operatorId={}, traceId={}",
                    projectId, sessionId, sourceRunId, operatorId, traceId, ex);
            throw BusinessException.of("todo persistence failed");
        }
    }

    @Transactional
    public List<SessionTodo> batchCreateTodos(Long projectId,
                                              Long sessionId,
                                              Long sourceRunId,
                                              List<SessionTodo> todos,
                                              Long operatorId,
                                              String traceId) {
        List<SessionTodo> created = new ArrayList<>();
        for (SessionTodo todo : todos == null ? List.<SessionTodo>of() : todos) {
            created.add(createTodo(projectId, sessionId, sourceRunId, todo, operatorId, traceId));
        }
        log.info("批量创建会话待办成功: projectId={}, sessionId={}, sourceRunId={}, createdCount={}, operatorId={}, traceId={}",
                projectId, sessionId, sourceRunId, created.size(), operatorId, traceId);
        return created;
    }

    @Transactional
    public List<SessionTodo> persistTodoPlan(Long projectId,
                                             Long sessionId,
                                             Long sourceRunId,
                                             TodoPlanView todoPlan,
                                             Long operatorId,
                                             String traceId) {
        List<SessionTodo> candidates = new ArrayList<>();
        if (todoPlan != null) {
            for (TodoPlanItemView item : todoPlan.items()) {
                if (item == null || !item.suggestedAutoCreate()) {
                    continue;
                }
                SessionTodo todo = new SessionTodo();
                todo.setTitle(normalize(item.title()));
                todo.setDescription(normalizeNullable(item.description()));
                todo.setSourceType(normalize(item.sourceType()));
                todo.setTodoStatus(normalize(item.recommendedStatus()));
                candidates.add(todo);
            }
        }
        return batchCreateTodos(projectId, sessionId, sourceRunId, candidates, operatorId, traceId);
    }

    public SessionTodo updateTodo(Long projectId,
                                  Long sessionId,
                                  Long todoId,
                                  Long sourceRunId,
                                  SessionTodo candidate,
                                  Long operatorId,
                                  String traceId) {
        SessionTodo existing = requireTodo(projectId, sessionId, todoId);
        existing.setSourceRunId(sourceRunId);
        existing.setTitle(normalize(candidate == null ? null : candidate.getTitle()));
        existing.setDescription(normalizeNullable(candidate == null ? null : candidate.getDescription()));
        existing.setSourceType(normalize(candidate == null ? null : candidate.getSourceType()));
        existing.setTodoStatus(normalize(candidate == null ? null : candidate.getTodoStatus()));
        validateTodo(existing);
        existing.setCompletedAt("DONE".equals(existing.getTodoStatus()) ? Instant.now() : null);
        try {
            int affected = sessionTodoRepository.update(existing);
            if (affected != 1) {
                log.error("更新会话待办失败: projectId={}, sessionId={}, todoId={}, operatorId={}, traceId={}, reason=update_failed",
                        projectId, sessionId, todoId, operatorId, traceId);
                throw BusinessException.of("todo persistence failed");
            }
            log.info("更新会话待办成功: projectId={}, sessionId={}, todoId={}, todoStatus={}, operatorId={}, traceId={}",
                    projectId, sessionId, todoId, existing.getTodoStatus(), operatorId, traceId);
            return requireTodo(projectId, sessionId, todoId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("更新会话待办异常: projectId={}, sessionId={}, todoId={}, operatorId={}, traceId={}",
                    projectId, sessionId, todoId, operatorId, traceId, ex);
            throw BusinessException.of("todo persistence failed");
        }
    }

    public SessionTodo completeTodo(Long projectId,
                                    Long sessionId,
                                    Long todoId,
                                    Long operatorId,
                                    String traceId) {
        requireTodo(projectId, sessionId, todoId);
        try {
            int affected = sessionTodoRepository.markCompleted(projectId, sessionId, todoId);
            if (affected != 1) {
                log.error("完成会话待办失败: projectId={}, sessionId={}, todoId={}, operatorId={}, traceId={}, reason=complete_failed",
                        projectId, sessionId, todoId, operatorId, traceId);
                throw BusinessException.of("todo persistence failed");
            }
            log.info("完成会话待办成功: projectId={}, sessionId={}, todoId={}, operatorId={}, traceId={}",
                    projectId, sessionId, todoId, operatorId, traceId);
            return requireTodo(projectId, sessionId, todoId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("完成会话待办异常: projectId={}, sessionId={}, todoId={}, operatorId={}, traceId={}",
                    projectId, sessionId, todoId, operatorId, traceId, ex);
            throw BusinessException.of("todo persistence failed");
        }
    }

    public void deleteTodo(Long projectId,
                           Long sessionId,
                           Long todoId,
                           Long operatorId,
                           String traceId) {
        requireTodo(projectId, sessionId, todoId);
        try {
            int affected = sessionTodoRepository.softDelete(projectId, sessionId, todoId);
            if (affected != 1) {
                log.error("删除会话待办失败: projectId={}, sessionId={}, todoId={}, operatorId={}, traceId={}, reason=delete_failed",
                        projectId, sessionId, todoId, operatorId, traceId);
                throw BusinessException.of("todo persistence failed");
            }
            log.info("删除会话待办成功: projectId={}, sessionId={}, todoId={}, operatorId={}, traceId={}",
                    projectId, sessionId, todoId, operatorId, traceId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("删除会话待办异常: projectId={}, sessionId={}, todoId={}, operatorId={}, traceId={}",
                    projectId, sessionId, todoId, operatorId, traceId, ex);
            throw BusinessException.of("todo persistence failed");
        }
    }

    private SessionTodo requireTodo(Long projectId, Long sessionId, Long todoId) {
        SessionTodo sessionTodo = sessionTodoRepository.findByTodoId(projectId, sessionId, todoId);
        if (sessionTodo == null) {
            throw BusinessException.of("Todo not found");
        }
        return sessionTodo;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeNullable(String value) {
        return value == null ? null : value.trim();
    }

    private void validateTodo(SessionTodo sessionTodo) {
        if (sessionTodo == null) {
            throw BusinessException.badRequest("todo payload must not be null");
        }
        if (sessionTodo.getTitle() == null || sessionTodo.getTitle().isBlank()) {
            throw BusinessException.badRequest("todo title must not be blank");
        }
        if (!ALLOWED_SOURCE_TYPES.contains(sessionTodo.getSourceType())) {
            throw BusinessException.badRequest("sourceType must be one of " + ALLOWED_SOURCE_TYPES);
        }
        if (!ALLOWED_TODO_STATUSES.contains(sessionTodo.getTodoStatus())) {
            throw BusinessException.badRequest("todoStatus must be one of " + ALLOWED_TODO_STATUSES);
        }
    }
}
