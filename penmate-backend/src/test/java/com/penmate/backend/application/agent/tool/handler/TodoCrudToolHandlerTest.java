package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.tool.definition.AgentToolDescriptor;
import com.penmate.backend.application.agent.tool.definition.TodoCrudToolDefinition;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.todo.TodoCrudApplicationService;
import com.penmate.backend.domain.todo.model.SessionTodo;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void exposes_the_provider_contract_and_classifies_only_list_as_read_only() {
        AgentToolDescriptor descriptor = new TodoCrudToolDefinition().descriptor();

        assertThat(descriptor.toolCode()).isEqualTo("todo_crud");
        assertThat(descriptor.exposure().parametersJsonSchema())
                .contains("\"operation\"", "\"list\"", "\"create\"", "\"update\"", "\"complete\"", "\"delete\"")
                .contains("\"sourceType\"", "\"todoStatus\"")
                .contains("\"required\": [\"operation\", \"sessionId\"]")
                .contains("\"additionalProperties\": false")
                .doesNotContain("\"get\"", "\"reorder\"", "\"status\"", "\"priority\"",
                        "\"taskId\"", "\"oneOf\"", "\"anyOf\"", "\"allOf\"", "Redis-backed");
        assertThat(descriptor.governancePolicy().defaultDecision().approvalRequired()).isFalse();
        assertThat(descriptor.governancePolicy().operationPolicies()).isEmpty();
        assertThat(handler.toolCode()).isEqualTo("todo_crud");
        assertThat(handler.mutatesState(request("{\"operation\":\"list\",\"sessionId\":9}", "list"))).isFalse();
        assertThat(handler.mutatesState(request("{\"operation\":\"create\",\"sessionId\":9}", "create"))).isTrue();
    }

    @Test
    void rejects_invalid_operation_shapes_and_business_ids() {
        List<InvalidCase> cases = List.of(
                new InvalidCase("{\"operation\":\"create\",\"sessionId\":9}", "title is required"),
                new InvalidCase("{\"operation\":\"list\",\"sessionId\":9,\"title\":\"unexpected\"}",
                        "Unexpected field for operation list: title"),
                new InvalidCase("{\"operation\":\"list\",\"sessionId\":0}",
                        "sessionId must be greater than or equal to 1"),
                new InvalidCase("{\"operation\":\"complete\",\"sessionId\":9,\"todoId\":-1}",
                        "todoId must be greater than or equal to 1"),
                new InvalidCase("{\"operation\":\"create\",\"sessionId\":9,\"sourceRunId\":0,\"title\":\"todo\",\"sourceType\":\"PLANNING\",\"todoStatus\":\"TODO\"}",
                        "sourceRunId must be greater than or equal to 1")
        );

        cases.forEach(testCase -> assertThatThrownBy(() -> handler.validate(request(testCase.json(), "invalid")))
                .as(testCase.message())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(testCase.message()));
    }

    @Test
    void creates_and_updates_todos_with_structured_business_id_output() {
        SessionTodo created = todo(20041L, 77L, "Create bridge scene", "TODO");
        when(service.createTodo(eq(1L), eq(9L), eq(77L), any(SessionTodo.class), eq(1001L), eq("trace-create")))
                .thenReturn(created);
        SessionTodo updated = todo(20041L, 88L, "Revise bridge scene", "BLOCKED");
        when(service.updateTodo(eq(1L), eq(9L), eq(20041L), eq(88L), any(SessionTodo.class), eq(1001L), eq("trace-update")))
                .thenReturn(updated);

        ToolCallResult createResult = handler.execute(request("""
                {"operation":"create","sessionId":9,"sourceRunId":77,"title":"Create bridge scene","sourceType":"PLANNING","todoStatus":"TODO"}
                """, "create"));
        ToolCallResult updateResult = handler.execute(request("""
                {"operation":"update","sessionId":9,"todoId":20041,"sourceRunId":88,"title":"Revise bridge scene","sourceType":"PLANNING","todoStatus":"BLOCKED"}
                """, "update"));

        assertThat(createResult.status()).isEqualTo("SUCCESS");
        assertThat(createResult.toolOutput())
                .contains("\"operation\":\"create\"", "\"todoId\":\"20041\"", "\"todoStatus\":\"TODO\"");
        assertThat(updateResult.status()).isEqualTo("SUCCESS");
        assertThat(updateResult.toolOutput())
                .contains("\"operation\":\"update\"", "\"sourceRunId\":\"88\"", "\"todoStatus\":\"BLOCKED\"")
                .doesNotContain("\"taskId\"");
    }

    @Test
    void lists_todos_as_structured_items() {
        when(service.listSessionTodos(1L, 9L, null)).thenReturn(List.of(
                todo(20031L, 77L, "Repair continuity", "BLOCKED"),
                todo(20032L, 77L, "Add bridge scene", "TODO")
        ));

        ToolCallResult result = handler.execute(request("{\"operation\":\"list\",\"sessionId\":9}", "list"));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.toolOutput())
                .contains("\"operation\":\"list\"", "\"items\"", "\"todoId\":\"20031\"", "\"todoId\":\"20032\"");
    }

    @Test
    void completes_and_deletes_todos_with_stable_outputs() {
        when(service.completeTodo(1L, 9L, 20031L, 1001L, "trace-complete"))
                .thenReturn(todo(20031L, 77L, "Repair continuity", "DONE"));

        ToolCallResult completeResult = handler.execute(request(
                "{\"operation\":\"complete\",\"sessionId\":9,\"todoId\":20031}", "complete"));
        ToolCallResult deleteResult = handler.execute(request(
                "{\"operation\":\"delete\",\"sessionId\":9,\"todoId\":20031}", "delete"));

        assertThat(completeResult.status()).isEqualTo("SUCCESS");
        assertThat(completeResult.toolOutput()).contains("\"operation\":\"complete\"", "\"todoStatus\":\"DONE\"");
        assertThat(deleteResult.status()).isEqualTo("SUCCESS");
        assertThat(deleteResult.toolOutput()).contains("\"operation\":\"delete\"", "\"deleted\":true");
    }

    private ToolCallRequest request(String json, String operation) {
        return new ToolCallRequest(1L, 77L, 9L, "todo_crud", json, 1001L,
                "trace-" + operation, "{}", "todo-call-1");
    }

    private SessionTodo todo(Long todoId, Long sourceRunId, String title, String status) {
        SessionTodo todo = new SessionTodo();
        todo.setTodoId(todoId);
        todo.setProjectId(1L);
        todo.setSessionId(9L);
        todo.setSourceRunId(sourceRunId);
        todo.setTitle(title);
        todo.setSourceType("PLANNING");
        todo.setTodoStatus(status);
        return todo;
    }

    private record InvalidCase(String json, String message) {
    }
}
