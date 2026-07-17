package com.penmate.backend.application.novel;

import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManuscriptPositionResolverTest {

    private final NovelGateway novelGateway = mock(NovelGateway.class);
    private final ManuscriptPositionResolver resolver = new ManuscriptPositionResolver(novelGateway);

    @Test
    void should_resolve_position_from_canonical_gateway_order_not_business_id_order() {
        NovelChapter first = chapter(9003L, 99);
        NovelChapter second = chapter(1001L, 1);
        NovelChapter third = chapter(4002L, 1);
        when(novelGateway.findChaptersByProjectId(77L)).thenReturn(List.of(first, second, third));

        ManuscriptPositionResolver.Resolution resolution = resolver.resolve(77L, 1001L);

        assertThat(resolution.resolved()).isTrue();
        assertThat(resolution.ordinal()).isEqualTo(1);
        assertThat(resolution.displayNo()).isEqualTo(2);
        assertThat(resolution.conflictCode()).isNull();
    }

    @Test
    void should_return_explicit_conflict_for_deleted_or_unknown_anchor() {
        when(novelGateway.findChaptersByProjectId(77L)).thenReturn(List.of(chapter(1001L, 1)));

        ManuscriptPositionResolver.Resolution resolution = resolver.resolve(77L, 9999L);

        assertThat(resolution.resolved()).isFalse();
        assertThat(resolution.ordinal()).isNull();
        assertThat(resolution.displayNo()).isNull();
        assertThat(resolution.conflictCode()).isEqualTo(ManuscriptPositionResolver.UNRESOLVED_ANCHOR);
    }

    private NovelChapter chapter(Long chapterId, int sortOrder) {
        NovelChapter chapter = new NovelChapter();
        chapter.setChapterId(chapterId);
        chapter.setSortOrder(sortOrder);
        return chapter;
    }
}
