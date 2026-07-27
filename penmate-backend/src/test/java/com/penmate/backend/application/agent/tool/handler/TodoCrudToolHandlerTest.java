package com.penmate.backend.application.agent.tool.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.tool.definition.TodoCrudToolDefinition;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.todo.TodoCrudApplicationService;
import com.penmate.backend.domain.todo.model.SessionTodo;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.penmate.backend.application.agent.tool.runtime.AgentToolTestContext.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TodoCrudToolHandlerTest {

    private final TodoCrudApplicationService service = mock(TodoCrudApplicationService.class);
    private final TodoCrudToolHandler handler = new TodoCrudToolHandler(
            service, new JacksonJsonCodec(new ObjectMapper()));

    @Test
    void keeps_crud_implementation_but_disables_agent_exposure() {
        var descriptor = new TodoCrudToolDefinition().descriptor();

        assertThat(descriptor.toolCode()).isEqualTo("todo_crud");
        assertThat(descriptor.exposure().parametersJsonSchema())
                .contains("\"operation\"", "\"list\"", "\"create\"", "\"update\"", "\"complete\"", "\"delete\"")
                .doesNotContain("planningMode", "userRequest", "qualityIssues", "sessionId");
        assertThat(descriptor.exposure().lifecycleStatus().name()).isEqualTo("DISABLED");
        assertThat(handler.mutatesState(todoContext(), request("{\"operation\":\"list\"}"))).isFalse();
        assertThat(handler.mutatesState(todoContext(), request("{\"operation\":\"create\",\"title\":\"x\",\"sourceType\":\"PLANNING\",\"todoStatus\":\"TODO\"}"))).isTrue();
    }

    @Test
    void validates_operation_shape_and_uses_runtime_session() {
        assertThatThrownBy(() -> handler.validate(todoContext(), request("{\"operation\":\"create\"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("title is required");
        assertThatThrownBy(() -> handler.validate(todoContext(), request("{\"operation\":\"list\",\"sessionId\":99}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unexpected field for operation list: sessionId");

        when(service.createTodo(eq(1L), eq(9L), eq(77L), any(SessionTodo.class), eq(1001L), eq("trace")))
                .thenReturn(todo(20041L, "Create bridge scene", "TODO"));
        var result = handler.execute(todoContext(), request("""
                {"operation":"create","title":"Create bridge scene","sourceType":"PLANNING","todoStatus":"TODO"}
                """));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.toolOutput()).contains("\"todoId\":\"20041\"", "\"operation\":\"create\"");
    }

    @Test
    void lists_completes_and_deletes_persisted_todos() {
        when(service.listSessionTodos(1L, 9L, null)).thenReturn(List.of(todo(20031L, "Repair continuity", "TODO")));
        when(service.completeTodo(1L, 9L, 20031L, 1001L, "trace"))
                .thenReturn(todo(20031L, "Repair continuity", "DONE"));

        assertThat(handler.execute(todoContext(), request("{\"operation\":\"list\"}")).toolOutput())
                .contains("\"items\"", "\"todoId\":\"20031\"");
        assertThat(handler.execute(todoContext(), request("{\"operation\":\"complete\",\"todoId\":20031}")).toolOutput())
                .contains("\"todoStatus\":\"DONE\"");
        assertThat(handler.execute(todoContext(), request("{\"operation\":\"delete\",\"todoId\":20031}")).toolOutput())
                .contains("\"deleted\":true");
    }

    private ToolCallRequest request(String args) {
        return new ToolCallRequest(77L, "todo_crud", args, "todo-call", null, 1L);
    }

    private com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext todoContext() {
        return context(1L, 77L, 9L, 8L, 1001L, 6L, 1L, null, "trace");
    }

    private SessionTodo todo(Long id, String title, String status) {
        SessionTodo todo = new SessionTodo();
        todo.setTodoId(id);
        todo.setTitle(title);
        todo.setSourceType("PLANNING");
        todo.setTodoStatus(status);
        return todo;
    }
}
