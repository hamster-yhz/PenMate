package com.penmate.backend.application.storybible;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.storybible.command.StoryBibleCommands;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleActorType;
import com.penmate.backend.domain.storybible.model.StoryBibleCanonStatus;
import com.penmate.backend.domain.storybible.model.StoryBibleCategory;
import com.penmate.backend.domain.storybible.model.StoryBibleChangeItem;
import com.penmate.backend.domain.storybible.model.StoryBibleChangeset;
import com.penmate.backend.domain.storybible.model.StoryBibleInclusionPolicy;
import com.penmate.backend.domain.storybible.model.StoryBibleNode;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeCategory;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeTag;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeType;
import com.penmate.backend.domain.storybible.model.StoryBibleProgression;
import com.penmate.backend.domain.storybible.model.StoryBibleRelation;
import com.penmate.backend.domain.storybible.model.StoryBibleSemanticFamily;
import com.penmate.backend.domain.storybible.model.StoryBibleTag;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoryBibleApplicationServiceTest {

    private final StoryBibleRepository repository = mock(StoryBibleRepository.class);
    private final StoryBibleChangesetService changesetService = mock(StoryBibleChangesetService.class);
    private final BusinessIdGenerator idGenerator = mock(BusinessIdGenerator.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StoryBibleSchemaValidator schemaValidator = new StoryBibleSchemaValidator(objectMapper);
    private final StoryBibleApplicationService service = new StoryBibleApplicationService(
            repository,
            changesetService,
            idGenerator,
            objectMapper,
            schemaValidator,
            new StoryBiblePatchValidator(objectMapper, schemaValidator),
            mock(StoryBibleEffectiveStateResolver.class),
            mock(StoryBibleProgressionReferenceValidator.class)
    );

    @Test
    void should_create_node_and_append_one_aggregate_changeset() {
        StoryBible root = root();
        StoryBibleNodeType type = new StoryBibleNodeType();
        type.setTypeId(33L);
        type.setSemanticFamily(StoryBibleSemanticFamily.CHARACTER);
        type.setFieldSchemaJson("{}");
        when(repository.findByProjectId(20L)).thenReturn(root);
        when(repository.findNodeType(10L, 33L)).thenReturn(type);
        when(repository.insertNode(any())).thenReturn(1);
        when(repository.findAliases(10L, 100L)).thenReturn(List.of());
        when(idGenerator.nextId()).thenReturn(100L);

        StoryBibleNode node = service.createNode(
                20L,
                new StoryBibleCommands.CreateNode(
                        33L, "Mira", "Pilot", "Body", "{\"age\":24}",
                        StoryBibleInclusionPolicy.AUTO_RETRIEVE, StoryBibleCanonStatus.CANON,
                        List.of(), List.of(), List.of()
                ),
                StoryBibleActorType.USER,
                9L,
                null
        );

        assertThat(node.getNodeId()).isEqualTo(100L);
        assertThat(node.getRevision()).isEqualTo(1L);
        assertThat(node.getCreatedBy()).isEqualTo(9L);
        verify(repository).insertNode(node);
        verify(changesetService).append(any(), any(), any(), any(), any(), any());
    }

    @Test
    void should_record_agent_metadata_mutations_with_run_provenance() {
        StoryBible root = root();
        when(repository.findByProjectId(20L)).thenReturn(root);
        when(repository.insertCategory(any())).thenReturn(1);
        when(idGenerator.nextId()).thenReturn(101L);

        StoryBibleCategory category = service.createCategory(
                20L,
                new StoryBibleCommands.CreateCategory(null, "Continuity", 3),
                StoryBibleActorType.AGENT,
                9L,
                88L
        );

        assertThat(category.getCategoryId()).isEqualTo(101L);
        verify(changesetService).append(eq(root), eq(StoryBibleActorType.AGENT), eq(9L), eq(88L),
                eq("Created category"), any());
    }

    @Test
    void should_validate_filters_and_delegate_bounded_story_bible_search() {
        StoryBible root = root();
        StoryBibleCategory category = new StoryBibleCategory();
        category.setCategoryId(31L);
        when(repository.findByProjectId(20L)).thenReturn(root);
        when(repository.findCategories(10L)).thenReturn(List.of(category));
        when(repository.findNodesFiltered(10L, 33L, "CANON", "Mira", 31L, null, 500))
                .thenReturn(List.of());

        service.searchNodes(20L, 33L, StoryBibleCanonStatus.CANON, " Mira ", 31L, null, 900);

        verify(repository).findNodesFiltered(10L, 33L, "CANON", "Mira", 31L, null, 500);
    }

    @Test
    void should_return_changeset_with_field_level_items_in_project_scope() {
        StoryBible root = root();
        StoryBibleChangeset changeset = new StoryBibleChangeset();
        changeset.setChangesetId(41L);
        StoryBibleChangeItem item = new StoryBibleChangeItem();
        item.setChangesetId(41L);
        when(repository.findByProjectId(20L)).thenReturn(root);
        when(repository.findChangeset(10L, 41L)).thenReturn(changeset);
        when(repository.findChangeItemsByChangesetIds(List.of(41L))).thenReturn(List.of(item));

        var result = service.getChangeset(20L, 41L);

        assertThat(result.changeset()).isSameAs(changeset);
        assertThat(result.items()).containsExactly(item);
    }

    @Test
    void should_record_alias_membership_changes_in_the_same_node_changeset() {
        StoryBible root = root();
        StoryBibleNodeType type = new StoryBibleNodeType();
        type.setTypeId(33L);
        type.setFieldSchemaJson("{}");
        when(repository.findByProjectId(20L)).thenReturn(root);
        when(repository.findNodeType(10L, 33L)).thenReturn(type);
        when(repository.insertNode(any())).thenReturn(1);
        when(repository.findAliases(10L, 100L)).thenReturn(List.of());
        when(repository.insertAlias(any())).thenReturn(1);
        when(idGenerator.nextId()).thenReturn(100L, 101L);

        service.createNode(20L, new StoryBibleCommands.CreateNode(
                        33L, "Mira", "Pilot", "Body", "{}",
                        StoryBibleInclusionPolicy.AUTO_RETRIEVE, StoryBibleCanonStatus.CANON,
                        List.of("Captain"), List.of(), List.of()),
                StoryBibleActorType.USER, 9L, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StoryBibleChangesetService.ChangeDraft>> drafts = ArgumentCaptor.forClass(List.class);
        verify(changesetService).append(eq(root), eq(StoryBibleActorType.USER), eq(9L), eq(null),
                eq("Created Story Bible node"), drafts.capture());
        assertThat(drafts.getValue()).extracting(StoryBibleChangesetService.ChangeDraft::fieldPath)
                .contains("/", "/aliases");
    }

    @Test
    void should_reject_memberships_outside_the_current_story_bible_before_writing() {
        StoryBible root = root();
        StoryBibleNodeType type = new StoryBibleNodeType();
        type.setTypeId(33L);
        type.setFieldSchemaJson("{}");
        when(repository.findByProjectId(20L)).thenReturn(root);
        when(repository.findNodeType(10L, 33L)).thenReturn(type);
        when(repository.findCategories(10L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createNode(20L, new StoryBibleCommands.CreateNode(
                        33L, "Mira", "Pilot", "Body", "{}",
                        StoryBibleInclusionPolicy.AUTO_RETRIEVE, StoryBibleCanonStatus.CANON,
                        List.of(), List.of(999L), List.of()),
                StoryBibleActorType.USER, 9L, null)).hasMessageContaining("category not found");

        verify(repository, never()).insertNode(any());
    }

    @Test
    void should_delete_node_relations_and_progressions_in_one_transactional_changeset() {
        StoryBible root = root();
        StoryBibleNode node = new StoryBibleNode();
        node.setNodeId(71L);
        node.setRevision(3L);
        StoryBibleRelation relation = new StoryBibleRelation();
        relation.setRelationId(81L);
        relation.setRevision(2L);
        StoryBibleProgression progression = new StoryBibleProgression();
        progression.setProgressionId(91L);
        progression.setRevision(4L);
        when(repository.findByProjectId(20L)).thenReturn(root);
        when(repository.findNode(10L, 71L)).thenReturn(node);
        when(repository.softDeleteNode(10L, 71L, 3L, 9L)).thenReturn(1);
        when(repository.findRelations(10L, List.of(71L))).thenReturn(List.of(relation));
        when(repository.findProgressions(10L, List.of(71L))).thenReturn(List.of(progression));
        when(repository.softDeleteRelation(10L, 81L, 2L, 9L)).thenReturn(1);
        when(repository.softDeleteProgression(10L, 91L, 4L, 9L)).thenReturn(1);

        service.deleteNode(20L, 71L, 3L, StoryBibleActorType.USER, 9L, null);

        verify(repository).softDeleteRelation(10L, 81L, 2L, 9L);
        verify(repository).softDeleteProgression(10L, 91L, 4L, 9L);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StoryBibleChangesetService.ChangeDraft>> drafts = ArgumentCaptor.forClass(List.class);
        verify(changesetService).append(eq(root), eq(StoryBibleActorType.USER), eq(9L), eq(null),
                eq("Deleted Story Bible node"), drafts.capture());
        assertThat(drafts.getValue()).extracting(StoryBibleChangesetService.ChangeDraft::entityType)
                .containsExactly("NODE", "RELATION", "PROGRESSION");
    }

    @Test
    void should_record_category_membership_removals_in_the_category_changeset() {
        StoryBible root = root();
        StoryBibleCategory category = new StoryBibleCategory();
        category.setCategoryId(31L);
        StoryBibleNodeCategory affected = nodeCategory(71L, 31L);
        when(repository.findByProjectId(20L)).thenReturn(root);
        when(repository.findCategories(10L)).thenReturn(List.of(category));
        when(repository.findNodeCategoriesByCategory(10L, 31L)).thenReturn(List.of(affected));
        when(repository.findNodeCategories(10L, 71L)).thenReturn(List.of(affected, nodeCategory(71L, 32L)));
        when(repository.softDeleteCategory(10L, 31L)).thenReturn(1);

        service.deleteCategory(20L, 31L, 9L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StoryBibleChangesetService.ChangeDraft>> drafts = ArgumentCaptor.forClass(List.class);
        verify(changesetService).append(eq(root), eq(StoryBibleActorType.USER), eq(9L), eq(null),
                eq("Deleted category"), drafts.capture());
        assertThat(drafts.getValue()).hasSize(2);
        assertThat(drafts.getValue().get(1)).extracting(
                StoryBibleChangesetService.ChangeDraft::entityType,
                StoryBibleChangesetService.ChangeDraft::entityId,
                StoryBibleChangesetService.ChangeDraft::fieldPath,
                StoryBibleChangesetService.ChangeDraft::beforeJson,
                StoryBibleChangesetService.ChangeDraft::afterJson)
                .containsExactly("NODE", 71L, "/categoryIds", "[31,32]", "[32]");
    }

    @Test
    void should_record_tag_membership_removals_in_the_tag_changeset() {
        StoryBible root = root();
        StoryBibleTag tag = new StoryBibleTag();
        tag.setTagId(41L);
        StoryBibleNodeTag affected = nodeTag(71L, 41L);
        when(repository.findByProjectId(20L)).thenReturn(root);
        when(repository.findTags(10L)).thenReturn(List.of(tag));
        when(repository.findNodeTagsByTag(10L, 41L)).thenReturn(List.of(affected));
        when(repository.findNodeTags(10L, 71L)).thenReturn(List.of(affected, nodeTag(71L, 42L)));
        when(repository.softDeleteTag(10L, 41L)).thenReturn(1);

        service.deleteTag(20L, 41L, 9L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StoryBibleChangesetService.ChangeDraft>> drafts = ArgumentCaptor.forClass(List.class);
        verify(changesetService).append(eq(root), eq(StoryBibleActorType.USER), eq(9L), eq(null),
                eq("Deleted tag"), drafts.capture());
        assertThat(drafts.getValue()).hasSize(2);
        assertThat(drafts.getValue().get(1).fieldPath()).isEqualTo("/tagIds");
        assertThat(drafts.getValue().get(1).beforeJson()).isEqualTo("[41,42]");
        assertThat(drafts.getValue().get(1).afterJson()).isEqualTo("[42]");
    }

    private StoryBibleNodeCategory nodeCategory(Long nodeId, Long categoryId) {
        StoryBibleNodeCategory membership = new StoryBibleNodeCategory();
        membership.setNodeId(nodeId);
        membership.setCategoryId(categoryId);
        return membership;
    }

    private StoryBibleNodeTag nodeTag(Long nodeId, Long tagId) {
        StoryBibleNodeTag membership = new StoryBibleNodeTag();
        membership.setNodeId(nodeId);
        membership.setTagId(tagId);
        return membership;
    }

    private StoryBible root() {
        StoryBible root = new StoryBible();
        root.setStoryBibleId(10L);
        root.setProjectId(20L);
        root.setContentRevision(4L);
        return root;
    }
}
