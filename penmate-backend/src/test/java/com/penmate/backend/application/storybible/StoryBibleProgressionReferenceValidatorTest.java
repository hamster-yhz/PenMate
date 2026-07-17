package com.penmate.backend.application.storybible;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.novel.ManuscriptPositionResolver;
import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleNode;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeType;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoryBibleProgressionReferenceValidatorTest {
    private final ManuscriptPositionResolver positions = mock(ManuscriptPositionResolver.class);
    private final StoryBibleRepository repository = mock(StoryBibleRepository.class);
    private final StoryBibleProgressionReferenceValidator validator =
            new StoryBibleProgressionReferenceValidator(positions, repository);

    @Test
    void accepts_ordered_project_chapters_and_an_event_node() {
        StoryBible bible = bible();
        when(positions.resolve(10L, 20L)).thenReturn(ManuscriptPositionResolver.Resolution.resolved(20L, 2, 3));
        when(positions.resolve(10L, 30L)).thenReturn(ManuscriptPositionResolver.Resolution.resolved(30L, 4, 5));
        StoryBibleNode event = new StoryBibleNode();
        event.setTypeId(50L);
        StoryBibleNodeType eventType = new StoryBibleNodeType();
        eventType.setTypeCode("EVENT");
        when(repository.findNode(11L, 40L)).thenReturn(event);
        when(repository.findNodeType(11L, 50L)).thenReturn(eventType);

        assertThatCode(() -> validator.validate(10L, bible, 20L, 30L, 40L))
                .doesNotThrowAnyException();
    }

    @Test
    void rejects_unresolved_or_reversed_manuscript_ranges() {
        StoryBible bible = bible();
        when(positions.resolve(10L, 20L)).thenReturn(ManuscriptPositionResolver.Resolution.unresolved(20L));
        assertThatThrownBy(() -> validator.validate(10L, bible, 20L, null, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("unresolved");

        when(positions.resolve(10L, 20L)).thenReturn(ManuscriptPositionResolver.Resolution.resolved(20L, 5, 6));
        when(positions.resolve(10L, 30L)).thenReturn(ManuscriptPositionResolver.Resolution.resolved(30L, 2, 3));
        assertThatThrownBy(() -> validator.validate(10L, bible, 20L, 30L, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("precedes");
    }

    @Test
    void rejects_story_event_references_that_are_not_event_nodes() {
        StoryBible bible = bible();
        when(positions.resolve(10L, 20L)).thenReturn(ManuscriptPositionResolver.Resolution.resolved(20L, 2, 3));
        StoryBibleNode character = new StoryBibleNode();
        character.setTypeId(50L);
        StoryBibleNodeType characterType = new StoryBibleNodeType();
        characterType.setTypeCode("CHARACTER");
        when(repository.findNode(11L, 40L)).thenReturn(character);
        when(repository.findNodeType(11L, 50L)).thenReturn(characterType);

        assertThatThrownBy(() -> validator.validate(10L, bible, 20L, null, 40L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("EVENT");
    }

    private StoryBible bible() {
        StoryBible bible = new StoryBible();
        bible.setStoryBibleId(11L);
        return bible;
    }
}
