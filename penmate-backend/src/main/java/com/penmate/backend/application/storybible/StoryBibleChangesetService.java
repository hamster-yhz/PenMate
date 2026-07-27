package com.penmate.backend.application.storybible;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.rag.RagSourceSyncService;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleActorType;
import com.penmate.backend.domain.storybible.model.StoryBibleChangeItem;
import com.penmate.backend.domain.storybible.model.StoryBibleChangeOperation;
import com.penmate.backend.domain.storybible.model.StoryBibleChangeset;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@Service
public class StoryBibleChangesetService {

    private final StoryBibleRepository repository;
    private final BusinessIdGenerator idGenerator;
    private RagSourceSyncService ragSourceSyncService;
    private final ThreadLocal<BatchAggregation> batchAggregation = new ThreadLocal<>();

    public StoryBibleChangesetService(StoryBibleRepository repository, BusinessIdGenerator idGenerator) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    @Autowired
    void setRagSourceSyncService(RagSourceSyncService ragSourceSyncService) {
        this.ragSourceSyncService = ragSourceSyncService;
    }

    public StoryBibleChangeset append(
            StoryBible storyBible,
            StoryBibleActorType actorType,
            Long actorId,
            Long sourceRunId,
            String summary,
            List<ChangeDraft> items
    ) {
        BatchAggregation aggregation = batchAggregation.get();
        if (aggregation != null) {
            aggregation.add(storyBible, actorType, actorId, sourceRunId, summary, items);
            StoryBibleChangeset pending = new StoryBibleChangeset();
            pending.setStoryBibleId(storyBible.getStoryBibleId());
            pending.setContentRevision(storyBible.getContentRevision());
            return pending;
        }
        long expectedRevision = storyBible.getContentRevision();
        if (repository.incrementContentRevision(storyBible.getStoryBibleId(), expectedRevision) != 1) {
            throw BusinessException.conflict("Story Bible content revision changed");
        }
        storyBible.setContentRevision(expectedRevision + 1);
        return persist(storyBible, actorType, actorId, sourceRunId, summary, items);
    }

    public <T> T aggregateSingleChangeset(Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        if (batchAggregation.get() != null) {
            throw new IllegalStateException("Nested Story Bible changeset aggregation is not supported");
        }
        BatchAggregation aggregation = new BatchAggregation();
        batchAggregation.set(aggregation);
        T result;
        try {
            result = action.get();
        } finally {
            batchAggregation.remove();
        }
        if (aggregation.storyBible != null && !aggregation.items.isEmpty()) {
            append(aggregation.storyBible, aggregation.actorType, aggregation.actorId,
                    aggregation.sourceRunId, aggregation.summary(), aggregation.items);
        }
        return result;
    }

    public StoryBibleChangeset appendInitial(
            StoryBible storyBible,
            StoryBibleActorType actorType,
            Long actorId,
            String summary,
            List<ChangeDraft> items
    ) {
        return persist(storyBible, actorType, actorId, null, summary, items);
    }

    private StoryBibleChangeset persist(
            StoryBible storyBible,
            StoryBibleActorType actorType,
            Long actorId,
            Long sourceRunId,
            String summary,
            List<ChangeDraft> items
    ) {
        StoryBibleChangeset changeset = new StoryBibleChangeset();
        changeset.setChangesetId(idGenerator.nextId());
        changeset.setStoryBibleId(storyBible.getStoryBibleId());
        changeset.setContentRevision(storyBible.getContentRevision());
        changeset.setActorType(actorType);
        changeset.setActorId(actorId);
        changeset.setSourceRunId(sourceRunId);
        changeset.setChangeSummary(summary == null || summary.isBlank() ? "Story Bible changed" : summary.trim());
        if (repository.insertChangeset(changeset) != 1) {
            throw BusinessException.of("Failed to persist Story Bible changeset");
        }
        for (ChangeDraft draft : items == null ? List.<ChangeDraft>of() : items) {
            StoryBibleChangeItem item = new StoryBibleChangeItem();
            item.setChangeItemId(idGenerator.nextId());
            item.setChangesetId(changeset.getChangesetId());
            item.setEntityType(draft.entityType());
            item.setEntityId(draft.entityId());
            item.setOperation(draft.operation());
            item.setFieldPath(draft.fieldPath());
            item.setBeforeJson(draft.beforeJson());
            item.setAfterJson(draft.afterJson());
            if (repository.insertChangeItem(item) != 1) {
                throw BusinessException.of("Failed to persist Story Bible change item");
            }
        }
        scheduleSourceSync(storyBible, actorId, items);
        return changeset;
    }

    private void scheduleSourceSync(StoryBible storyBible, Long actorId, List<ChangeDraft> items) {
        if (ragSourceSyncService == null || storyBible == null || actorId == null || items == null) return;
        items.stream().filter(item -> "NODE".equals(item.entityType())).map(ChangeDraft::entityId).distinct()
                .forEach(nodeId -> syncNode(storyBible.getProjectId(), actorId, nodeId, items));
        if (items.stream().anyMatch(item -> "ALIAS".equals(item.entityType())
                || "NODE_TYPE".equals(item.entityType()))) {
            ragSourceSyncService.scheduleAllStoryBibleNodes(storyBible.getProjectId(), actorId);
        }
    }

    private void syncNode(Long projectId, Long actorId, Long nodeId, List<ChangeDraft> items) {
        boolean deleted = items.stream().anyMatch(item -> "NODE".equals(item.entityType())
                && Objects.equals(nodeId, item.entityId())
                && (item.operation() == StoryBibleChangeOperation.DELETE
                || item.operation() == StoryBibleChangeOperation.ARCHIVE));
        if (deleted) ragSourceSyncService.delete(projectId, actorId, RagSourceSyncService.STORY_BIBLE, nodeId);
        else ragSourceSyncService.scheduleStoryBibleNode(projectId, actorId, nodeId);
    }

    public record ChangeDraft(
            String entityType,
            Long entityId,
            StoryBibleChangeOperation operation,
            String fieldPath,
            String beforeJson,
            String afterJson
    ) {
    }

    private static final class BatchAggregation {
        private StoryBible storyBible;
        private StoryBibleActorType actorType;
        private Long actorId;
        private Long sourceRunId;
        private final List<String> summaries = new ArrayList<>();
        private final List<ChangeDraft> items = new ArrayList<>();

        private void add(StoryBible nextStoryBible, StoryBibleActorType nextActorType,
                         Long nextActorId, Long nextSourceRunId, String summary,
                         List<ChangeDraft> drafts) {
            if (storyBible == null) {
                storyBible = nextStoryBible;
                actorType = nextActorType;
                actorId = nextActorId;
                sourceRunId = nextSourceRunId;
            } else if (!Objects.equals(storyBible.getStoryBibleId(), nextStoryBible.getStoryBibleId())
                    || actorType != nextActorType || !Objects.equals(actorId, nextActorId)
                    || !Objects.equals(sourceRunId, nextSourceRunId)) {
                throw new IllegalStateException("Story Bible batch crossed an aggregation boundary");
            }
            if (summary != null && !summary.isBlank()) summaries.add(summary.trim());
            if (drafts != null) items.addAll(drafts);
        }

        private String summary() {
            String joined = String.join("; ", summaries);
            if (joined.isBlank()) return "Story Bible batch changed";
            return joined.length() <= 500 ? joined : joined.substring(0, 500);
        }
    }
}
