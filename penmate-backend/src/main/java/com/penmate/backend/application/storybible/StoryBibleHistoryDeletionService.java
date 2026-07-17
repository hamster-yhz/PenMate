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
    public void deleteVerifiedArchive(Long storyBibleId, List<Long> changesetIds, int expectedItemCount) {
        if (changesetIds == null || changesetIds.isEmpty()) return;
        int deletedItems = repository.deleteChangeItemsByChangesetIds(changesetIds);
        if (deletedItems != expectedItemCount) {
            throw new IllegalStateException("Story Bible archive item count changed before deletion");
        }
        int deletedChangesets = repository.deleteChangesetsByIds(storyBibleId, changesetIds);
        if (deletedChangesets != changesetIds.size()) {
            throw new IllegalStateException("Story Bible archive changeset count changed before deletion");
        }
    }
}
