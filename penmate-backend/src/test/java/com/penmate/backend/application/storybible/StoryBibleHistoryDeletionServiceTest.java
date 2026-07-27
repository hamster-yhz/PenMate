package com.penmate.backend.application.storybible;

import com.penmate.backend.domain.storybible.model.StoryBibleChangeItem;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoryBibleHistoryDeletionServiceTest {

    @Test
    void marks_verified_changesets_archived_without_deleting_items() {
        StoryBibleRepository repository = mock(StoryBibleRepository.class);
        StoryBibleHistoryDeletionService service = new StoryBibleHistoryDeletionService(repository);
        List<Long> ids = List.of(1L, 2L);
        Instant archivedAt = Instant.parse("2026-07-26T00:00:00Z");
        when(repository.findChangeItemsByChangesetIds(ids))
                .thenReturn(List.of(new StoryBibleChangeItem(), new StoryBibleChangeItem()));
        when(repository.archiveChangesets(9L, ids, archivedAt)).thenReturn(2);

        service.markVerifiedArchive(9L, ids, 2, archivedAt);

        verify(repository).archiveChangesets(9L, ids, archivedAt);
    }

    @Test
    void refuses_archival_when_item_count_changed() {
        StoryBibleRepository repository = mock(StoryBibleRepository.class);
        StoryBibleHistoryDeletionService service = new StoryBibleHistoryDeletionService(repository);
        List<Long> ids = List.of(1L);
        when(repository.findChangeItemsByChangesetIds(ids)).thenReturn(List.of());

        assertThatThrownBy(() -> service.markVerifiedArchive(9L, ids, 1, Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }
}
