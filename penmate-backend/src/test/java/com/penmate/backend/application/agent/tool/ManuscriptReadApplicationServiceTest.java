package com.penmate.backend.application.agent.tool;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManuscriptReadApplicationServiceTest {
    private final NovelGateway novels = mock(NovelGateway.class);
    private final ManuscriptReadApplicationService service = new ManuscriptReadApplicationService(novels);

    @BeforeEach
    void projectExists() {
        NovelProject project = new NovelProject();
        project.setProjectId(1L);
        when(novels.findProjectById(1L)).thenReturn(project);
    }

    @Test
    void reads_arbitrary_unicode_ranges_with_exact_metadata() {
        NovelChapter chapter = chapter(10L, "A😀BC", 4L);
        when(novels.findChapterByIdAndProjectId(1L, 10L)).thenReturn(chapter);

        var result = service.read(1L, List.of(new ManuscriptReadApplicationService.Selection(10L, 1, 3)));

        assertThat(result.totalCharacters()).isEqualTo(2);
        assertThat(result.chapters()).singleElement().satisfies(slice -> {
            assertThat(slice.content()).isEqualTo("😀B");
            assertThat(slice.characterCount()).isEqualTo(4);
            assertThat(slice.contentRevision()).isEqualTo(4L);
            assertThat(slice.contentHash()).hasSize(64);
            assertThat(slice.isComplete()).isFalse();
        });
    }

    @Test
    void rejects_the_complete_call_instead_of_truncating_over_limit() {
        NovelChapter first = chapter(10L, "a".repeat(10_001), 1L);
        NovelChapter second = chapter(11L, "b".repeat(10_000), 1L);
        when(novels.findChapterByIdAndProjectId(1L, 10L)).thenReturn(first);
        when(novels.findChapterByIdAndProjectId(1L, 11L)).thenReturn(second);

        assertThatThrownBy(() -> service.read(1L, List.of(
                new ManuscriptReadApplicationService.Selection(10L, null, null),
                new ManuscriptReadApplicationService.Selection(11L, null, null))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exceeds 20000");
    }

    private NovelChapter chapter(Long id, String content, Long revision) {
        NovelChapter chapter = new NovelChapter();
        chapter.setChapterId(id);
        chapter.setVolumeId(2L);
        chapter.setTitle("Chapter");
        chapter.setContent(content);
        chapter.setContentRevision(revision);
        return chapter;
    }
}
