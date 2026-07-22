package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.novel.NovelApplicationService;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookCrudToolHandlerTest {

    private final NovelApplicationService novelApplicationService = mock(NovelApplicationService.class);
    private final BookCrudToolHandler handler = new BookCrudToolHandler(
            novelApplicationService, new JacksonJsonCodec(new ObjectMapper()));

    @Test
    void classifies_mutations_and_accepts_the_minimal_read_request() {
        ToolCallRequest list = request("{\"operation\":\"list\"}");

        assertThat(handler.mutatesState(list)).isFalse();
        assertThat(handler.mutatesState(request("{\"operation\":\"create\"}"))).isTrue();
        handler.validate(list);
    }

    @Test
    void rejects_invalid_operation_shapes() {
        List<InvalidCase> cases = List.of(
                new InvalidCase("{}", "operation is required"),
                new InvalidCase("{\"operation\":\"archive\"}", "Unsupported operation: archive"),
                new InvalidCase("{\"operation\":\"create\",\"title\":\"Three Body\"}", "ownerUserId is required"),
                new InvalidCase("{\"operation\":\"create\",\"ownerUserId\":1001,\"title\":\"   \"}", "title is required"),
                new InvalidCase("{\"operation\":\"update\",\"title\":\"New title\"}", "projectId is required"),
                new InvalidCase("{\"operation\":\"delete\"}", "projectId is required"),
                new InvalidCase("{\"operation\":\"list\",\"projectId\":9001}", "Unexpected field for operation list: projectId"),
                new InvalidCase("{\"operation\":\"delete\",\"projectId\":9001,\"title\":\"unexpected\"}",
                        "Unexpected field for operation delete: title"),
                new InvalidCase("{\"operation\":\"update\",\"projectId\":9001,\"ownerUserId\":1001}",
                        "Unexpected field for operation update: ownerUserId"),
                new InvalidCase("{", "invalid tool args")
        );

        cases.forEach(testCase -> assertThatThrownBy(() -> handler.validate(request(testCase.json())))
                .as(testCase.message())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(testCase.message()));
    }

    @Test
    void maps_unexpected_service_failure_to_a_stable_result() {
        when(novelApplicationService.listProjects()).thenThrow(new RuntimeException("boom"));

        ToolCallResult result = handler.execute(request("{\"operation\":\"list\"}"));

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("BOOK_CRUD_EXECUTION_FAILED");
        assertThat(result.errorMessage()).isEqualTo("boom");
    }

    @Test
    void returns_business_ids_and_nullable_summary_for_created_projects() {
        NovelProject created = new NovelProject();
        created.setProjectId(66061336829952L);
        created.setOwnerUserId(1001L);
        created.setTitle("Archive at the Edge of the Mist");
        created.setSummary(null);
        created.setStatus(1);
        when(novelApplicationService.createProject(any(), eq("trace-1"))).thenReturn(created);

        ToolCallResult result = handler.execute(request("""
                {"operation":"create","ownerUserId":1001,"title":"Archive at the Edge of the Mist"}
                """));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(AgentJsonCodec.parseObj(result.toolOutput()).getLong("projectId")).isEqualTo(66061336829952L);
        assertThat(AgentJsonCodec.parseObj(result.toolOutput()).containsKey("summary")).isTrue();
        assertThat(result.toolOutput()).contains("\"summary\":null");
    }

    private ToolCallRequest request(String toolArgsJson) {
        return new ToolCallRequest(9001L, 8001L, 7001L, "book_crud", toolArgsJson,
                1001L, "trace-1", "{}", "idem-1");
    }

    private record InvalidCase(String json, String message) {
    }
}
