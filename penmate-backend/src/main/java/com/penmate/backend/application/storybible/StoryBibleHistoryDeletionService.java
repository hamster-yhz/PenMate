package com.penmate.backend.application.storybible;

import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StoryBibleHistoryDeletionService {

    private final StoryBibleRepository repository;

    public StoryBibleHistoryDeletionService(StoryBibleRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void markVerifiedArchive(Long storyBibleId, List<Long> changesetIds, int expectedItemCount,
                                    java.time.Instant archivedAt) {
        if (changesetIds == null || changesetIds.isEmpty()) return;
        int actualItems = repository.findChangeItemsByChangesetIds(changesetIds).size();
        if (actualItems != expectedItemCount) {
            throw new IllegalStateException("Story Bible archive item count changed before archival");
        }
        int archived = repository.archiveChangesets(storyBibleId, changesetIds, archivedAt);
        if (archived != changesetIds.size()) {
            throw new IllegalStateException("Story Bible archive changeset count changed before archival");
        }
    }
}
