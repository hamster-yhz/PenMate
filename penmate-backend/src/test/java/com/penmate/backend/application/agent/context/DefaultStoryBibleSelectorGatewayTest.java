package com.penmate.backend.application.agent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmGateway;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultStoryBibleSelectorGatewayTest {
    private final AgentLlmGateway llm = mock(AgentLlmGateway.class);
    private final DefaultStoryBibleSelectorGateway gateway = new DefaultStoryBibleSelectorGateway(llm, new ObjectMapper());
    private final AgentLlmExecutionConfig config = AgentLlmExecutionConfig.builder().providerCode("test").build();

    @Test
    void should_parse_structured_selection_and_validate_ids() {
        when(llm.generateTurn(any(), any())).thenReturn(new AgentLlmTurnResponse(
                "stop", "{\"selectedNodeIds\":[\"2\"],\"reasons\":{\"2\":\"named character\"}}", List.of(), "{}"));
        var selection = gateway.select("write Mira", catalog(), config);
        assertThat(selection.nodeIds()).containsExactly(2L);
        assertThat(selection.reasons()).containsEntry(2L, "named character");
    }

    @Test
    void should_reject_invented_ids() {
        when(llm.generateTurn(any(), any())).thenReturn(new AgentLlmTurnResponse(
                "stop", "{\"selectedNodeIds\":[\"999\"],\"reasons\":{}}", List.of(), "{}"));
        assertThatThrownBy(() -> gateway.select("write Mira", catalog(), config))
                .isInstanceOf(BusinessException.class).hasMessageContaining("unknown node ID");
    }

    private List<StoryBibleRouteRequest.CatalogEntry> catalog() {
        return List.of(new StoryBibleRouteRequest.CatalogEntry(2L, "Mira", "CHARACTER", "Pilot", "AUTO_RETRIEVE", "CANON"));
    }
}
