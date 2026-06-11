package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.todo.TodoCrudApplicationService;
import com.penmate.backend.domain.todo.model.SessionTodo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class TodoCrudToolHandlerTest {

    @Test
    void should_expose_task_state_operations_in_schema() {
        String schema = new com.penmate.backend.application.agent.tool.definition.TodoCrudToolDefinition()
                .descriptor()
                .exposure()
                .parametersJsonSchema();

        assertThat(schema)
                .contains("\"get\"")
                .contains("\"reorder\"")
                .contains("\"status\"")
                .contains("in_progress")
                .contains("blockedReason")
                .contains("orderedTodoIds")
                .doesNotContain("\"oneOf\"");
    }

    @Test
    void should_validate_create_requires_title_only_for_task_payload() {
        TodoCrudToolHandler handler = new TodoCrudToolHandler(mock(TodoCrudApplicationService.class));

        assertThatThrownBy(() -> handler.validate(request("{\"operation\":\"create\",\"sessionId\":9}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("title is required");
    }

    @Test
    void should_execute_create_and_return_status_payload() {
        TodoCrudApplicationService service = mock(TodoCrudApplicationService.class);
        SessionTodo todo = todo(2001L, "Inspect login routes", "in_progress");
        doReturn(todo).when(service).createTodo(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.eq(77L), org.mockito.ArgumentMatchers.any(SessionTodo.class),
                org.mockito.ArgumentMatchers.eq(1001L), org.mockito.ArgumentMatchers.eq("trace"));
        TodoCrudToolHandler handler = new TodoCrudToolHandler(service);

        ToolCallResult result = handler.execute(request("""
                {"operation":"create","sessionId":9,"taskId":77,"title":"Inspect login routes","status":"in_progress","summary":"started"}
                """));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.toolOutput())
                .contains("\"operation\":\"create\"")
                .contains("\"todoId\":\"2001\"")
                .contains("\"status\":\"in_progress\"");
    }

    @Test
    void should_execute_reorder_and_return_items() {
        TodoCrudApplicationService service = mock(TodoCrudApplicationService.class);
        doReturn(List.of(todo(2001L, "A", "pending"), todo(2002L, "B", "pending")))
                .when(service).reorderTodos(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(9L),
                        org.mockito.ArgumentMatchers.eq(List.of(2002L, 2001L)),
                        org.mockito.ArgumentMatchers.eq(1001L), org.mockito.ArgumentMatchers.eq("trace"));
        TodoCrudToolHandler handler = new TodoCrudToolHandler(service);

        ToolCallResult result = handler.execute(request("""
                {"operation":"reorder","sessionId":9,"orderedTodoIds":[2002,2001]}
                """));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.toolOutput())
                .contains("\"operation\":\"reorder\"")
                .contains("\"items\"")
                .contains("\"todoId\":\"2001\"")
                .contains("\"todoId\":\"2002\"");
    }

    private static ToolCallRequest request(String args) {
        return new ToolCallRequest(1L, 77L, 9L, "todo_crud", args, 1001L, "trace", "{}", "todo-call-1");
    }

    private static SessionTodo todo(Long todoId, String title, String status) {
        SessionTodo todo = new SessionTodo();
        todo.setTodoId(todoId);
        todo.setProjectId(1L);
        todo.setSessionId(9L);
        todo.setTaskId(77L);
        todo.setTitle(title);
        todo.setDescription(title);
        todo.setStatus(status);
        todo.setPriority(100);
        todo.setOrderIndex(todoId.intValue());
        todo.setCreatedAt(LocalDateTime.of(2026, 6, 10, 10, 0));
        todo.setUpdatedAt(LocalDateTime.of(2026, 6, 10, 10, 5));
        return todo;
    }
}
