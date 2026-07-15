package com.penmate.backend.application.agent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.storybible.StoryBibleEffectiveStateResolver;
import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleNode;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeType;
import com.penmate.backend.domain.storybible.model.StoryBibleRelation;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoryBibleContextResolverTest {
    private final StoryBibleCandidateRetriever candidates = mock(StoryBibleCandidateRetriever.class);
    private final StoryBibleSelectorGateway selector = mock(StoryBibleSelectorGateway.class);
    private final StoryBibleRepository repository = mock(StoryBibleRepository.class);
    private final StoryBibleEffectiveStateResolver effective = mock(StoryBibleEffectiveStateResolver.class);
    private final StoryBibleContextResolver resolver = new StoryBibleContextResolver(candidates, selector, repository, effective);

    @Test
    void retrieval_mode_should_not_call_llm_and_should_expand_one_hop_relations() {
        stubGraph();
        var result = resolver.resolve(request(StoryBibleRoutingMode.RETRIEVAL));

        verify(selector, never()).select(any(), anyList(), any());
        assertThat(result.decision().selectedNodeIds()).containsExactly(1L);
        assertThat(result.nodes()).extracting(StoryBibleContextResolver.RenderedNode::nodeId).containsExactly(1L, 2L);
        assertThat(result.decision().selectorUsed()).isFalse();
    }

    @Test
    void retrieval_then_llm_should_send_only_dynamic_candidates() {
        stubGraph();
        when(selector.select(any(), anyList(), any())).thenReturn(new StoryBibleSelectorGateway.Selection(List.of(1L), Map.of()));
        resolver.resolve(request(StoryBibleRoutingMode.RETRIEVAL_THEN_LLM));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StoryBibleRouteRequest.CatalogEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(selector).select(any(), captor.capture(), any());
        assertThat(captor.getValue()).extracting(StoryBibleRouteRequest.CatalogEntry::nodeId).containsExactly(1L);
    }

    private void stubGraph() {
        StoryBible root = new StoryBible();
        root.setStoryBibleId(10L);
        when(candidates.retrieve(any())).thenReturn(new StoryBibleCandidateRetriever.Retrieval(root,
                List.of(new StoryBibleCandidateRetriever.Candidate(1L, 50, "lexical")), true));
        StoryBibleRelation relation = new StoryBibleRelation();
        relation.setSourceNodeId(1L);
        relation.setTargetNodeId(2L);
        when(repository.findRelations(10L, List.of(1L))).thenReturn(List.of(relation));
        when(repository.findNodesByIds(eq(10L), anyList())).thenReturn(List.of(node(1L), node(2L)));
        StoryBibleNodeType type = new StoryBibleNodeType();
        type.setTypeId(5L);
        type.setTypeCode("CHARACTER");
        when(repository.findNodeTypes(10L)).thenReturn(List.of(type));
        when(repository.findProgressions(eq(10L), anyList())).thenReturn(List.of());
        when(effective.resolve(any(), any(), any(), any(), anyList())).thenReturn(
                new StoryBibleEffectiveStateResolver.EffectiveState(new ObjectMapper().createObjectNode(),
                        List.of(), List.of(), List.of(), true));
    }

    private StoryBibleNode node(long id) {
        StoryBibleNode node = new StoryBibleNode();
        node.setNodeId(id);
        node.setTypeId(5L);
        node.setTitle("N" + id);
        return node;
    }

    private StoryBibleRouteRequest request(StoryBibleRoutingMode mode) {
        return new StoryBibleRouteRequest(20L, 30L, 40L, 50L, "Mira", List.of(), mode, null,
                List.of(
                        new StoryBibleRouteRequest.CatalogEntry(1L, "Mira", "CHARACTER", "Pilot", "AUTO_RETRIEVE", "CANON"),
                        new StoryBibleRouteRequest.CatalogEntry(99L, "Tower", "LOCATION", "Place", "AUTO_RETRIEVE", "CANON")
                ), List.of(), com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig.builder().providerCode("test").build());
    }
}
