package com.penmate.backend.application.agent.context;

import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleAlias;
import com.penmate.backend.domain.storybible.model.StoryBibleCanonStatus;
import com.penmate.backend.domain.storybible.model.StoryBibleInclusionPolicy;
import com.penmate.backend.domain.storybible.model.StoryBibleNode;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoryBibleCandidateRetrieverTest {
    @Test
    void should_merge_always_include_exact_alias_and_lexical_candidates() {
        StoryBibleRepository repository = mock(StoryBibleRepository.class);
        StoryBible root = new StoryBible();
        root.setStoryBibleId(10L);
        when(repository.findByProjectId(20L)).thenReturn(root);
        when(repository.findAlwaysIncludeNodes(10L))
                .thenReturn(List.of(node(1L, StoryBibleInclusionPolicy.ALWAYS_INCLUDE)));
        StoryBibleAlias alias = new StoryBibleAlias();
        alias.setNodeId(2L);
        when(repository.findByNormalizedAlias(10L, "mira")).thenReturn(List.of(alias));
        when(repository.searchNodesLexically(eq(10L), anyList(), eq(40)))
                .thenReturn(List.of(node(2L, StoryBibleInclusionPolicy.AUTO_RETRIEVE),
                        node(3L, StoryBibleInclusionPolicy.AUTO_RETRIEVE)));
        StoryBibleCandidateRetriever retriever = new StoryBibleCandidateRetriever(
                repository, new NoopStoryBibleSemanticRetriever());

        var result = retriever.retrieve(new StoryBibleRouteRequest(
                20L, 30L, 40L, 50L, "Mira enters the tower", List.of("Mira"),
                StoryBibleRoutingMode.RETRIEVAL, null, List.of(
                        new StoryBibleRouteRequest.CatalogEntry(1L, "Core", "CORE", "", "ALWAYS_INCLUDE", "CANON"),
                        new StoryBibleRouteRequest.CatalogEntry(2L, "Mira", "CHARACTER", "", "AUTO_RETRIEVE", "CANON"),
                        new StoryBibleRouteRequest.CatalogEntry(3L, "Tower", "LOCATION", "", "AUTO_RETRIEVE", "CANON")
                ), List.of(), null));

        assertThat(result.candidates()).extracting(StoryBibleCandidateRetriever.Candidate::nodeId)
                .containsExactly(1L, 2L, 3L);
        assertThat(result.candidates().get(1).reasons()).contains("exact_alias:Mira", "lexical");
        assertThat(result.semanticUnavailable()).isTrue();
    }

    private StoryBibleNode node(Long id, StoryBibleInclusionPolicy policy) {
        StoryBibleNode node = new StoryBibleNode();
        node.setNodeId(id);
        node.setInclusionPolicy(policy);
        return node;
    }
}
