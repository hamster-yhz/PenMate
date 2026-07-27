package com.penmate.backend.application.storybible;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.storybible.model.*;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StoryBibleUndoServiceTest {
    private final StoryBibleRepository repository = mock(StoryBibleRepository.class);
    private final StoryBibleChangesetService changesets = mock(StoryBibleChangesetService.class);
    private final BusinessIdGenerator ids = mock(BusinessIdGenerator.class);
    private final JsonCodec json = new JacksonJsonCodec(new ObjectMapper());
    private final StoryBibleUndoService service = new StoryBibleUndoService(repository, changesets, ids, json);

    @Test
    void undoes_every_changeset_from_the_latest_agent_run_in_reverse_order() {
        StoryBible root = root(3L);
        StoryBibleChangeset first = changeset(101L, 2L, 700L);
        StoryBibleChangeset second = changeset(102L, 3L, 700L);
        StoryBibleNode firstNode = node(201L, "First");
        StoryBibleNode secondNode = node(202L, "Second");
        StoryBibleChangeItem firstItem = createdNode(301L, first, firstNode);
        StoryBibleChangeItem secondItem = createdNode(302L, second, secondNode);
        StoryBibleChangeset undo = new StoryBibleChangeset();
        undo.setChangesetId(900L);

        when(repository.findByProjectId(20L)).thenReturn(root);
        when(repository.findChangesetsBySourceRun(10L, 700L)).thenReturn(List.of(first, second));
        when(repository.findChangesetsByRevisionRange(10L, 2L, 3L)).thenReturn(List.of(first, second));
        when(repository.findChangeItemsByChangesetIds(List.of(101L, 102L))).thenReturn(List.of(firstItem, secondItem));
        when(repository.findNodeIncludingDeleted(10L, 201L)).thenReturn(firstNode);
        when(repository.findNodeIncludingDeleted(10L, 202L)).thenReturn(secondNode);
        when(repository.softDeleteNode(eq(10L), anyLong(), eq(1L), eq(9L))).thenReturn(1);
        when(changesets.append(eq(root), eq(StoryBibleActorType.USER), eq(9L), isNull(),
                contains("700"), anyList())).thenReturn(undo);
        when(repository.markChangesetsUndone(10L, List.of(101L, 102L), 9L, 900L)).thenReturn(2);

        var result = service.undoRun(20L, 700L, 9L);

        assertThat(result.sourceRunId()).isEqualTo(700L);
        assertThat(result.changesetIds()).containsExactly(101L, 102L);
        assertThat(result.undoChangeset()).isSameAs(undo);
        InOrder order = inOrder(repository);
        order.verify(repository).softDeleteNode(10L, 202L, 1L, 9L);
        order.verify(repository).softDeleteNode(10L, 201L, 1L, 9L);
        verify(changesets).append(eq(root), eq(StoryBibleActorType.USER), eq(9L), isNull(),
                contains("700"), argThat(drafts -> drafts.size() == 2));
        verify(repository).markChangesetsUndone(10L, List.of(101L, 102L), 9L, 900L);
    }

    @Test
    void rejects_an_interleaved_run_before_touching_current_state() {
        StoryBible root = root(3L);
        StoryBibleChangeset first = changeset(101L, 2L, 700L);
        StoryBibleChangeset second = changeset(102L, 3L, 700L);
        StoryBibleChangeset other = changeset(199L, 2L, 701L);
        when(repository.findByProjectId(20L)).thenReturn(root);
        when(repository.findChangesetsBySourceRun(10L, 700L)).thenReturn(List.of(first, second));
        when(repository.findChangesetsByRevisionRange(10L, 2L, 3L)).thenReturn(List.of(other, second));

        assertThatThrownBy(() -> service.undoRun(20L, 700L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("夹有其他修改");

        verify(repository, never()).findChangeItemsByChangesetIds(anyList());
        verify(repository, never()).softDeleteNode(anyLong(), anyLong(), anyLong(), anyLong());
        verifyNoInteractions(changesets);
    }

    @Test
    void rejects_a_changed_final_entity_without_applying_any_inverse() {
        StoryBible root = root(3L);
        StoryBibleChangeset target = changeset(102L, 3L, 700L);
        StoryBibleNode recorded = node(202L, "Recorded");
        StoryBibleNode current = node(202L, "Changed later");
        StoryBibleChangeItem item = createdNode(302L, target, recorded);
        when(repository.findByProjectId(20L)).thenReturn(root);
        when(repository.findChangesetsBySourceRun(10L, 700L)).thenReturn(List.of(target));
        when(repository.findChangesetsByRevisionRange(10L, 3L, 3L)).thenReturn(List.of(target));
        when(repository.findChangeItemsByChangesetIds(List.of(102L))).thenReturn(List.of(item));
        when(repository.findNodeIncludingDeleted(10L, 202L)).thenReturn(current);

        assertThatThrownBy(() -> service.undoRun(20L, 700L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("NODE 202");

        verify(repository, never()).softDeleteNode(anyLong(), anyLong(), anyLong(), anyLong());
        verifyNoInteractions(changesets);
    }

    @Test
    void rejects_an_archived_agent_run_before_reading_change_items() {
        StoryBibleChangeset target = changeset(101L, 1L, 700L);
        target.setArchivedAt(Instant.now());
        when(repository.findByProjectId(20L)).thenReturn(root(1L));
        when(repository.findChangesetsBySourceRun(10L, 700L)).thenReturn(List.of(target));

        assertThatThrownBy(() -> service.undoRun(20L, 700L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("归档变更不可撤回");

        verify(repository, never()).findChangesetsByRevisionRange(anyLong(), anyLong(), anyLong());
        verify(repository, never()).findChangeItemsByChangesetIds(anyList());
    }

    @Test
    void rejects_an_agent_run_after_the_seven_day_undo_window() {
        StoryBibleChangeset target = changeset(101L, 1L, 700L);
        target.setCreatedAt(Instant.now().minus(java.time.Duration.ofDays(8)));
        when(repository.findByProjectId(20L)).thenReturn(root(1L));
        when(repository.findChangesetsBySourceRun(10L, 700L)).thenReturn(List.of(target));

        assertThatThrownBy(() -> service.undoRun(20L, 700L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超过 7 天");

        verify(repository, never()).findChangesetsByRevisionRange(anyLong(), anyLong(), anyLong());
        verify(repository, never()).findChangeItemsByChangesetIds(anyList());
    }

    private StoryBible root(long revision) {
        StoryBible root = new StoryBible();
        root.setStoryBibleId(10L);
        root.setProjectId(20L);
        root.setContentRevision(revision);
        return root;
    }

    private StoryBibleChangeset changeset(long id, long revision, long runId) {
        StoryBibleChangeset changeset = new StoryBibleChangeset();
        changeset.setId(id);
        changeset.setChangesetId(id);
        changeset.setStoryBibleId(10L);
        changeset.setContentRevision(revision);
        changeset.setActorType(StoryBibleActorType.AGENT);
        changeset.setActorId(9L);
        changeset.setSourceRunId(runId);
        changeset.setCreatedAt(Instant.now());
        return changeset;
    }

    private StoryBibleNode node(long id, String title) {
        StoryBibleNode node = new StoryBibleNode();
        node.setNodeId(id);
        node.setStoryBibleId(10L);
        node.setTypeId(1L);
        node.setTitle(title);
        node.setAttributesJson("{}");
        node.setInclusionPolicy(StoryBibleInclusionPolicy.AUTO_RETRIEVE);
        node.setCanonStatus(StoryBibleCanonStatus.CANON);
        node.setRevision(1L);
        return node;
    }

    private StoryBibleChangeItem createdNode(long itemId, StoryBibleChangeset changeset, StoryBibleNode node) {
        StoryBibleChangeItem item = new StoryBibleChangeItem();
        item.setChangeItemId(itemId);
        item.setChangesetId(changeset.getChangesetId());
        item.setEntityType("NODE");
        item.setEntityId(node.getNodeId());
        item.setOperation(StoryBibleChangeOperation.CREATE);
        item.setFieldPath("/");
        item.setAfterJson(json.write(node));
        return item;
    }
}
