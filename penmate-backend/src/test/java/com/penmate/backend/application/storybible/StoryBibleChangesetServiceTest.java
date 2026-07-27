package com.penmate.backend.application.storybible;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleActorType;
import com.penmate.backend.domain.storybible.model.StoryBibleChangeOperation;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class StoryBibleChangesetServiceTest {

    private final StoryBibleRepository repository = mock(StoryBibleRepository.class);
    private final BusinessIdGenerator idGenerator = mock(BusinessIdGenerator.class);
    private final StoryBibleChangesetService service = new StoryBibleChangesetService(repository, idGenerator);

    @Test
    void should_increment_content_revision_once_and_write_one_changeset() {
        StoryBible root = root(7L);
        when(repository.incrementContentRevision(10L, 7L)).thenReturn(1);
        when(repository.insertChangeset(any())).thenReturn(1);
        when(repository.insertChangeItem(any())).thenReturn(1);
        when(idGenerator.nextId()).thenReturn(100L, 101L);

        var changeset = service.append(
                root, StoryBibleActorType.USER, 9L, null, "Update node",
                List.of(new StoryBibleChangesetService.ChangeDraft(
                        "NODE", 55L, StoryBibleChangeOperation.UPDATE, "/title", "\"old\"", "\"new\""
                ))
        );

        assertThat(root.getContentRevision()).isEqualTo(8L);
        assertThat(changeset.getContentRevision()).isEqualTo(8L);
        verify(repository).incrementContentRevision(10L, 7L);
        verify(repository).insertChangeset(any());
        verify(repository).insertChangeItem(any());
    }

    @Test
    void should_reject_stale_root_revision_without_writing_history() {
        StoryBible root = root(7L);
        when(repository.incrementContentRevision(10L, 7L)).thenReturn(0);

        assertThatThrownBy(() -> service.append(
                root, StoryBibleActorType.USER, 9L, null, "Update", List.of()
        )).isInstanceOf(BusinessException.class).hasMessage("Story Bible content revision changed");
    }

    @Test
    void aggregates_multiple_mutations_into_one_revision_and_changeset() {
        StoryBible firstView = root(7L);
        StoryBible secondView = root(7L);
        when(repository.incrementContentRevision(10L, 7L)).thenReturn(1);
        when(repository.insertChangeset(any())).thenReturn(1);
        when(repository.insertChangeItem(any())).thenReturn(1);
        when(idGenerator.nextId()).thenReturn(100L, 101L, 102L);

        String result = service.aggregateSingleChangeset(() -> {
            service.append(firstView, StoryBibleActorType.AGENT, 9L, 88L, "First", List.of(
                    new StoryBibleChangesetService.ChangeDraft(
                            "NODE", 51L, StoryBibleChangeOperation.UPDATE, "/title", "\"a\"", "\"b\"")));
            service.append(secondView, StoryBibleActorType.AGENT, 9L, 88L, "Second", List.of(
                    new StoryBibleChangesetService.ChangeDraft(
                            "NODE", 52L, StoryBibleChangeOperation.CREATE, "/", null, "{}")));
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        verify(repository).incrementContentRevision(10L, 7L);
        verify(repository).insertChangeset(any());
        verify(repository, times(2)).insertChangeItem(any());
    }

    private StoryBible root(long revision) {
        StoryBible root = new StoryBible();
        root.setStoryBibleId(10L);
        root.setProjectId(20L);
        root.setContentRevision(revision);
        return root;
    }
}
