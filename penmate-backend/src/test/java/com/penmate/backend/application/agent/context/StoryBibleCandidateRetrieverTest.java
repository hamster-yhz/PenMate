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
        assertThat(result.trace().semanticRetrieverAvailable()).isFalse();
        assertThat(result.trace().alwaysIncludeCount()).isEqualTo(1);
        assertThat(result.trace().exactAliasCount()).isEqualTo(1);
        assertThat(result.trace().lexicalCandidateCount()).isEqualTo(2);
        assertThat(result.trace().mergedCandidateCount()).isEqualTo(3);
        assertThat(result.trace().candidates()).extracting(StoryBibleRetrievalTrace.Candidate::nodeId)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void should_detect_catalog_aliases_directly_from_the_user_message() {
        StoryBibleRepository repository = mock(StoryBibleRepository.class);
        StoryBible root = new StoryBible();
        root.setStoryBibleId(10L);
        when(repository.findByProjectId(20L)).thenReturn(root);
        StoryBibleCandidateRetriever retriever = new StoryBibleCandidateRetriever(
                repository, new NoopStoryBibleSemanticRetriever());

        var result = retriever.retrieve(new StoryBibleRouteRequest(
                20L, 30L, 40L, 50L, "Captain enters the tower", List.of(),
                StoryBibleRoutingMode.RETRIEVAL, null, List.of(
                new StoryBibleRouteRequest.CatalogEntry(2L, "CHARACTER", "PERSON", "Mira",
                        List.of("Captain"), "Pilot", List.of(), "", "AUTO_RETRIEVE", "CANON")
        ), List.of(), null));

        assertThat(result.candidates()).extracting(StoryBibleCandidateRetriever.Candidate::nodeId)
                .containsExactly(2L);
        assertThat(result.trace().exactAliasCount()).isEqualTo(1);
        assertThat(result.candidates().getFirst().reasons()).containsExactly("exact_alias:Captain");
    }

    @Test
    void should_detect_cjk_aliases_without_whitespace_boundaries() {
        StoryBibleRepository repository = mock(StoryBibleRepository.class);
        StoryBible root = new StoryBible();
        root.setStoryBibleId(10L);
        when(repository.findByProjectId(20L)).thenReturn(root);
        StoryBibleCandidateRetriever retriever = new StoryBibleCandidateRetriever(
                repository, new NoopStoryBibleSemanticRetriever());

        var result = retriever.retrieve(new StoryBibleRouteRequest(
                20L, 30L, 40L, 50L, "船长走进高塔", List.of(), StoryBibleRoutingMode.RETRIEVAL, null,
                List.of(new StoryBibleRouteRequest.CatalogEntry(2L, "CHARACTER", "PERSON", "Mira",
                        List.of("船长"), "Pilot", List.of(), "", "AUTO_RETRIEVE", "CANON")),
                List.of(), null));

        assertThat(result.trace().exactAliasCount()).isEqualTo(1);
        assertThat(result.candidates()).extracting(StoryBibleCandidateRetriever.Candidate::nodeId)
                .containsExactly(2L);
    }

    private StoryBibleNode node(Long id, StoryBibleInclusionPolicy policy) {
        StoryBibleNode node = new StoryBibleNode();
        node.setNodeId(id);
        node.setInclusionPolicy(policy);
        return node;
    }
}
