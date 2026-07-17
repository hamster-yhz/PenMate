package com.penmate.backend.application.storybible;

import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoryBibleHistoryDeletionServiceTest {

    @Test
    void should_delete_items_before_changesets_and_verify_counts() {
        StoryBibleRepository repository = mock(StoryBibleRepository.class);
        StoryBibleHistoryDeletionService service = new StoryBibleHistoryDeletionService(repository);
        List<Long> ids = List.of(1L, 2L);
        when(repository.deleteChangeItemsByChangesetIds(ids)).thenReturn(3);
        when(repository.deleteChangesetsByIds(9L, ids)).thenReturn(2);

        service.deleteVerifiedArchive(9L, ids, 3);

        var order = inOrder(repository);
        order.verify(repository).deleteChangeItemsByChangesetIds(ids);
        order.verify(repository).deleteChangesetsByIds(9L, ids);
    }

    @Test
    void should_abort_before_changeset_delete_when_item_count_changed() {
        StoryBibleRepository repository = mock(StoryBibleRepository.class);
        StoryBibleHistoryDeletionService service = new StoryBibleHistoryDeletionService(repository);
        List<Long> ids = List.of(1L);
        when(repository.deleteChangeItemsByChangesetIds(ids)).thenReturn(0);

        assertThatThrownBy(() -> service.deleteVerifiedArchive(9L, ids, 1))
                .isInstanceOf(IllegalStateException.class);
        verify(repository).deleteChangeItemsByChangesetIds(ids);
    }
}
