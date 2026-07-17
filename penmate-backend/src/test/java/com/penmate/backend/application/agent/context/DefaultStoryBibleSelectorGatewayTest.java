package com.penmate.backend.application.agent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.prompt.SystemPromptBundle;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultStoryBibleSelectorGatewayTest {
    private final AgentLlmGateway llm = mock(AgentLlmGateway.class);
    private final DefaultStoryBibleSelectorGateway gateway = new DefaultStoryBibleSelectorGateway(
            llm, new ObjectMapper(), (stage, profile) -> new SystemPromptBundle(stage, profile, List.of(), "selector prompt"));
    private final AgentLlmExecutionConfig config = AgentLlmExecutionConfig.builder().providerCode("test").build();

    @Test
    void should_parse_structured_selection_and_validate_ids() {
        when(llm.generateTurn(any(), any())).thenReturn(new AgentLlmTurnResponse(
                "tool_calls", "", List.of(toolCall("""
                        {"intentTags":["WRITE"],"selectedNodeIds":["2"],"relationExpansion":["3"],
                         "selectionReasons":{"2":"named character"},"missingContextFlags":[],"confidence":0.9}
                        """)), "{}", new LlmTokenUsage(20, 5, 25, 7, 0)));
        var selection = gateway.select("write Mira", catalog(), config);
        assertThat(selection.nodeIds()).containsExactly(2L);
        assertThat(selection.relationExpansionNodeIds()).containsExactly(3L);
        assertThat(selection.reasons()).containsEntry(2L, "named character");
        assertThat(selection.intentTags()).containsExactly("WRITE");
        assertThat(selection.tokenUsage().cachedPromptTokens()).isEqualTo(7);

        ArgumentCaptor<AgentLlmTurnRequest> request = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(llm).generateTurn(request.capture(), any());
        assertThat(request.getValue().toolChoice()).isEqualTo("required");
        assertThat(request.getValue().tools()).singleElement()
                .extracting(tool -> tool.toolCode()).isEqualTo("select_story_bible_context");
        assertThat(request.getValue().messages().getFirst().content()).contains("SELECTOR_CATALOG_JSON", "Mira");
        assertThat(request.getValue().messages().get(1).content()).doesNotContain("SELECTOR_CATALOG_JSON");
    }

    @Test
    void should_reject_invented_ids() {
        when(llm.generateTurn(any(), any())).thenReturn(new AgentLlmTurnResponse(
                "tool_calls", "", List.of(toolCall("""
                        {"intentTags":[],"selectedNodeIds":["999"],"relationExpansion":[],
                         "selectionReasons":{},"missingContextFlags":[],"confidence":0.2}
                        """)), "{}"));
        assertThatThrownBy(() -> gateway.select("write Mira", catalog(), config))
                .isInstanceOf(BusinessException.class).hasMessageContaining("unknown node ID");
    }

    @Test
    void retrieval_then_llm_keeps_candidates_out_of_the_stable_system_message() {
        when(llm.generateTurn(any(), any())).thenReturn(new AgentLlmTurnResponse(
                "tool_calls", "", List.of(toolCall("""
                        {"intentTags":[],"selectedNodeIds":[],"relationExpansion":[],
                         "selectionReasons":{},"missingContextFlags":[],"confidence":0.5}
                        """)), "{}"));

        gateway.select(new StoryBibleSelectorGateway.SelectorRequest(
                StoryBibleRoutingMode.RETRIEVAL_THEN_LLM, "write Mira", catalog(), List.of(2L)), config);

        ArgumentCaptor<AgentLlmTurnRequest> request = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(llm).generateTurn(request.capture(), any());
        assertThat(request.getValue().messages().getFirst().content()).isEqualTo("selector prompt");
        assertThat(request.getValue().messages().get(1).content())
                .contains("RETRIEVAL_CANDIDATES_JSON", "WORKING_SET_NODE_IDS", "Mira");
    }

    private List<StoryBibleRouteRequest.CatalogEntry> catalog() {
        return List.of(
                new StoryBibleRouteRequest.CatalogEntry(2L, "CHARACTER", "CHARACTER", "Mira", List.of("Captain"),
                        "Pilot", List.of(new StoryBibleRouteRequest.CatalogRelation("OUT", "ALLY_OF", 3L, "Nox")),
                        "{}", "AUTO_RETRIEVE", "CANON"),
                new StoryBibleRouteRequest.CatalogEntry(3L, "Nox", "CHARACTER", "Guard", "AUTO_RETRIEVE", "CANON")
        );
    }

    private AgentLlmToolCall toolCall(String arguments) {
        return new AgentLlmToolCall("call-1", "select_story_bible_context", arguments);
    }
}
