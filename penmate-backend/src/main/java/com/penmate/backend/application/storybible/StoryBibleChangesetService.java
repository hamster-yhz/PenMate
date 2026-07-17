package com.penmate.backend.application.storybible;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleActorType;
import com.penmate.backend.domain.storybible.model.StoryBibleChangeItem;
import com.penmate.backend.domain.storybible.model.StoryBibleChangeOperation;
import com.penmate.backend.domain.storybible.model.StoryBibleChangeset;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class StoryBibleChangesetService {

    private final StoryBibleRepository repository;
    private final BusinessIdGenerator idGenerator;

    public StoryBibleChangesetService(StoryBibleRepository repository, BusinessIdGenerator idGenerator) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    public StoryBibleChangeset append(
            StoryBible storyBible,
            StoryBibleActorType actorType,
            Long actorId,
            Long sourceRunId,
            String summary,
            List<ChangeDraft> items
    ) {
        long expectedRevision = storyBible.getContentRevision();
        if (repository.incrementContentRevision(storyBible.getStoryBibleId(), expectedRevision) != 1) {
            throw BusinessException.conflict("Story Bible content revision changed");
        }
        storyBible.setContentRevision(expectedRevision + 1);
        return persist(storyBible, actorType, actorId, sourceRunId, summary, items);
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
        return changeset;
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
}
