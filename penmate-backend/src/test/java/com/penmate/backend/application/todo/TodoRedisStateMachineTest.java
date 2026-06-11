package com.penmate.backend.application.todo;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.todo.model.SessionTodo;
import com.penmate.backend.domain.todo.repository.SessionTodoRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TodoRedisStateMachineTest {

    @Test
    void should_enforce_external_task_state_machine_for_session_todos() {
        InMemorySessionTodoRepository repository = new InMemorySessionTodoRepository();
        TodoCrudApplicationService service = new TodoCrudApplicationService(repository, nextIdGenerator());

        SessionTodo first = todo("Inspect login routes", "pending");
        SessionTodo created = service.createTodo(1L, 9L, 77L, first, 1001L, "trace-1");
        assertThat(created.getStatus()).isEqualTo("pending");
        assertThat(created.getOrderIndex()).isEqualTo(1);

        SessionTodo inProgress = todo("Inspect login routes", "in_progress");
        SessionTodo updated = service.updateTodo(1L, 9L, created.getTodoId(), 77L, inProgress, 1001L, "trace-2");
        assertThat(updated.getStatus()).isEqualTo("in_progress");

        SessionTodo second = service.createTodo(1L, 9L, 77L, todo("Patch auth service", "pending"), 1001L, "trace-3");
        assertThatThrownBy(() -> service.updateTodo(1L, 9L, second.getTodoId(), 77L, todo("Patch auth service", "in_progress"), 1001L, "trace-4"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("only one in_progress todo is allowed per session");

        assertThatThrownBy(() -> service.updateTodo(1L, 9L, created.getTodoId(), 77L, todo("Inspect login routes", "blocked"), 1001L, "trace-5"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("blockedReason");

        SessionTodo blocked = todo("Inspect login routes", "blocked");
        blocked.setBlockedReason("Need product confirmation");
        assertThat(service.updateTodo(1L, 9L, created.getTodoId(), 77L, blocked, 1001L, "trace-6").getBlockedReason())
                .isEqualTo("Need product confirmation");

        assertThatThrownBy(() -> service.updateTodo(1L, 9L, created.getTodoId(), 77L, todo("Inspect login routes", "failed"), 1001L, "trace-7"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("errorSummary");

        SessionTodo completed = todo("Inspect login routes", "completed");
        completed.setSummary("Found login entry in routes/login.ts");
        SessionTodo done = service.updateTodo(1L, 9L, created.getTodoId(), 77L, completed, 1001L, "trace-8");
        assertThat(done.getCompletedAt()).isNotNull();
        assertThat(done.getSummary()).isEqualTo("Found login entry in routes/login.ts");
    }

    private static SessionTodo todo(String title, String status) {
        SessionTodo todo = new SessionTodo();
        todo.setTitle(title);
        todo.setDescription(title + " details");
        todo.setStatus(status);
        todo.setPriority(100);
        todo.setMetadata("{\"owner\":\"main_agent\"}");
        return todo;
    }

    private static BusinessIdGenerator nextIdGenerator() {
        AtomicLong sequence = new AtomicLong(1000L);
        return sequence::incrementAndGet;
    }

    private static class InMemorySessionTodoRepository implements SessionTodoRepository {
        private List<SessionTodo> todos = new ArrayList<>();

        @Override
        public List<SessionTodo> findBySession(Long projectId, Long sessionId, String status) {
            return todos.stream()
                    .filter(todo -> projectId.equals(todo.getProjectId()))
                    .filter(todo -> sessionId.equals(todo.getSessionId()))
                    .filter(todo -> status == null || status.isBlank() || status.equals(todo.getStatus()))
                    .sorted(Comparator.comparing(SessionTodo::getOrderIndex, Comparator.nullsLast(Integer::compareTo)))
                    .map(this::copy)
                    .toList();
        }

        @Override
        public SessionTodo findByTodoId(Long projectId, Long sessionId, Long todoId) {
            return findBySession(projectId, sessionId, null).stream()
                    .filter(todo -> todoId.equals(todo.getTodoId()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public void saveSessionTodos(Long projectId, Long sessionId, List<SessionTodo> nextTodos) {
            todos = nextTodos.stream().map(this::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }

        private SessionTodo copy(SessionTodo source) {
            SessionTodo copy = new SessionTodo();
            copy.setTodoId(source.getTodoId());
            copy.setProjectId(source.getProjectId());
            copy.setSessionId(source.getSessionId());
            copy.setTaskId(source.getTaskId());
            copy.setTitle(source.getTitle());
            copy.setDescription(source.getDescription());
            copy.setStatus(source.getStatus());
            copy.setPriority(source.getPriority());
            copy.setOrderIndex(source.getOrderIndex());
            copy.setSummary(source.getSummary());
            copy.setBlockedReason(source.getBlockedReason());
            copy.setErrorSummary(source.getErrorSummary());
            copy.setMetadata(source.getMetadata());
            copy.setCreatedAt(source.getCreatedAt());
            copy.setUpdatedAt(source.getUpdatedAt());
            copy.setCompletedAt(source.getCompletedAt());
            return copy;
        }
    }
}
