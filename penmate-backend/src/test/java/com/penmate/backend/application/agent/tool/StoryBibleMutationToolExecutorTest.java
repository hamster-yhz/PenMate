package com.penmate.backend.application.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.tool.StoryBibleMutationToolExecutor.OperationSpec;
import com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoryBibleMutationToolExecutorTest {

    private final StoryBibleUpdateApplicationService updateService = mock(StoryBibleUpdateApplicationService.class);
    private final JacksonJsonCodec jsonCodec = new JacksonJsonCodec(new ObjectMapper());
    private final StoryBibleMutationToolExecutor executor = new StoryBibleMutationToolExecutor(updateService, jsonCodec);

    @Test
    void converts_structured_attributes_to_one_transactional_node_mutation() {
        AuthorizedAgentRunContext context = mock(AuthorizedAgentRunContext.class);
        ToolCallRequest request = request("""
                {"items":[{"operation":"update","nodeId":71,"expectedRevision":4,
                 "attributes":{"coreMotivation":"protect the city","pointOfView":true}}]}
                """);
        when(updateService.executeBatch(any(), any())).thenReturn(ToolCallResult.success("{}"));

        ToolCallResult result = executor.execute(context, request, Map.of(
                "update", OperationSpec.of("update_node", "nodeId", "expectedRevision")));

        assertThat(result.status()).isEqualTo("SUCCESS");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StoryBibleUpdateApplicationService.MutationCommand>> captured = ArgumentCaptor.forClass(List.class);
        verify(updateService).executeBatch(any(), captured.capture());
        assertThat(captured.getValue()).singleElement().extracting(StoryBibleUpdateApplicationService.MutationCommand::mutationKind)
                .isEqualTo("update_node");
        Map<String, Object> mutation = captured.getValue().getFirst().mutation();
        assertThat(mutation.get("nodeId")).isEqualTo(71);
        assertThat(mutation.get("expectedRevision")).isEqualTo(4);
        assertThat(mutation).doesNotContainKeys("operation", "kind");
        assertThat(mutation.containsKey("attributes")).isFalse();
        assertThat(jsonCodec.readObject((String) mutation.get("attributesJson")))
                .containsEntry("coreMotivation", "protect the city")
                .containsEntry("pointOfView", true);
    }

    @Test
    void rejects_an_update_without_an_inspected_revision() {
        ToolCallRequest request = request("{\"items\":[{\"operation\":\"update\",\"nodeId\":71}]}");
        Map<String, OperationSpec> operations = Map.of(
                "update", OperationSpec.of("update_node", "nodeId", "expectedRevision"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> executor.validate(request, operations))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedRevision");
        ToolCallResult result = executor.execute(mock(AuthorizedAgentRunContext.class), request, operations);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorMessage()).contains("expectedRevision");
        verify(updateService, never()).executeBatch(any(), any());
    }

    private ToolCallRequest request(String arguments) {
        return new ToolCallRequest(42L, "story_bible_node_write", arguments,
                "idem", 1, "call-1", null, null, null, null, null, 9L);
    }
}
