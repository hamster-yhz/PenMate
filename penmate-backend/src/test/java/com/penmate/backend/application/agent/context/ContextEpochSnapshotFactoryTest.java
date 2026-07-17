package com.penmate.backend.application.agent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.storybible.StoryBibleEffectiveStateResolver;
import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleAlias;
import com.penmate.backend.domain.storybible.model.StoryBibleCanonStatus;
import com.penmate.backend.domain.storybible.model.StoryBibleInclusionPolicy;
import com.penmate.backend.domain.storybible.model.StoryBibleNode;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeType;
import com.penmate.backend.domain.storybible.model.StoryBibleRelation;
import com.penmate.backend.domain.storybible.model.StoryBibleSemanticFamily;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextEpochSnapshotFactoryTest {

    @Test
    void creates_effective_core_and_selector_manifest_for_the_bound_chapter() {
        StoryBibleRepository bibles = mock(StoryBibleRepository.class);
        NovelGateway novels = mock(NovelGateway.class);
        StoryBibleEffectiveStateResolver states = mock(StoryBibleEffectiveStateResolver.class);
        ObjectMapper mapper = new ObjectMapper();
        ContextEpochSnapshotFactory factory = new ContextEpochSnapshotFactory(bibles, novels, states, mapper);

        StoryBible root = new StoryBible();
        root.setStoryBibleId(10L);
        root.setContentRevision(7L);
        when(bibles.findByProjectId(1L)).thenReturn(root);
        NovelProject project = new NovelProject();
        project.setStructureRevision(4L);
        when(novels.findProjectById(1L)).thenReturn(project);
        NovelChapter chapter = new NovelChapter();
        chapter.setContentRevision(3L);
        when(novels.findChapterByIdAndProjectId(1L, 50L)).thenReturn(chapter);

        StoryBibleNodeType type = new StoryBibleNodeType();
        type.setTypeId(20L);
        type.setTypeCode("CHARACTER");
        type.setSemanticFamily(StoryBibleSemanticFamily.CHARACTER);
        type.setSortOrder(1);
        when(bibles.findNodeTypes(10L)).thenReturn(List.of(type));
        StoryBibleNode mira = node(30L, "Mira", StoryBibleInclusionPolicy.ALWAYS_INCLUDE);
        StoryBibleNode nox = node(31L, "Nox", StoryBibleInclusionPolicy.AUTO_RETRIEVE);
        when(bibles.findNodes(10L, null, StoryBibleCanonStatus.CANON.name(), null)).thenReturn(List.of(mira, nox));
        StoryBibleAlias alias = new StoryBibleAlias();
        alias.setNodeId(30L);
        alias.setAlias("Captain Mira");
        when(bibles.findAliasesByNodeIds(10L, List.of(30L, 31L))).thenReturn(List.of(alias));
        StoryBibleRelation relation = new StoryBibleRelation();
        relation.setSourceNodeId(30L);
        relation.setTargetNodeId(31L);
        relation.setRelationType("ALLY_OF");
        when(bibles.findRelations(10L, List.of(30L, 31L))).thenReturn(List.of(relation));
        when(bibles.findProgressions(10L, List.of(30L, 31L))).thenReturn(List.of());
        when(states.resolve(eq(1L), eq(50L), any(), eq(type), anyList())).thenAnswer(invocation -> {
            StoryBibleNode node = invocation.getArgument(2);
            var state = mapper.createObjectNode().put("title", node.getTitle() + " at chapter 5")
                    .put("summary", node.getSummary());
            state.set("attributes", mapper.createObjectNode());
            return new StoryBibleEffectiveStateResolver.EffectiveState(state, List.of(99L), List.of(), List.of(), true);
        });

        var snapshot = factory.create(1L, 50L);

        assertThat(snapshot.schemaVersion()).isEqualTo(2);
        assertThat(snapshot.coreContext()).singleElement().satisfies(core -> {
            assertThat(core.effectiveState().path("title").asText()).isEqualTo("Mira at chapter 5");
            assertThat(core.appliedProgressionIds()).containsExactly(99L);
        });
        assertThat(snapshot.selectorCatalog()).first().satisfies(entry -> {
            assertThat(entry.semanticFamily()).isEqualTo("CHARACTER");
            assertThat(entry.aliases()).containsExactly("Captain Mira");
            assertThat(entry.keyRelations()).singleElement().satisfies(edge -> {
                assertThat(edge.direction()).isEqualTo("OUT");
                assertThat(edge.otherNodeId()).isEqualTo(31L);
            });
            assertThat(entry.currentChapterStateSummary()).contains("Mira at chapter 5");
        });
    }

    private StoryBibleNode node(Long id, String title, StoryBibleInclusionPolicy policy) {
        StoryBibleNode node = new StoryBibleNode();
        node.setNodeId(id);
        node.setTypeId(20L);
        node.setTitle(title);
        node.setSummary("summary");
        node.setInclusionPolicy(policy);
        node.setCanonStatus(StoryBibleCanonStatus.CANON);
        return node;
    }
}
