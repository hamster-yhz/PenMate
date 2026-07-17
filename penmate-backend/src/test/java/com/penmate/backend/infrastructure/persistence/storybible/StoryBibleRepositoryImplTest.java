package com.penmate.backend.infrastructure.persistence.storybible;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StoryBibleRepositoryImplTest {

    @Test
    void should_treat_empty_relation_and_progression_filters_as_list_all() {
        StoryBibleMapper mapper = mock(StoryBibleMapper.class);
        StoryBibleRepositoryImpl repository = new StoryBibleRepositoryImpl(mapper);

        assertThat(repository.findNodesByIds(1L, List.of())).isEmpty();
        assertThat(repository.findRelations(1L, List.of())).isEmpty();
        assertThat(repository.findProgressions(1L, null)).isEmpty();
        verify(mapper).findRelations(1L, List.of());
        verify(mapper).findProgressions(1L, null);
    }
}
