package com.penmate.backend.application.storybible;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.novel.ManuscriptPositionResolver;
import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.storybible.model.StoryBibleNode;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeType;
import com.penmate.backend.domain.storybible.model.StoryBibleProgression;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoryBibleEffectiveStateResolverTest {

    private final NovelGateway novelGateway = mock(NovelGateway.class);
    private StoryBibleEffectiveStateResolver resolver;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        StoryBibleSchemaValidator schemaValidator = new StoryBibleSchemaValidator(objectMapper);
        StoryBiblePatchValidator patchValidator = new StoryBiblePatchValidator(objectMapper, schemaValidator);
        resolver = new StoryBibleEffectiveStateResolver(
                new ManuscriptPositionResolver(novelGateway),
                patchValidator,
                new StoryBibleConflictDetector(),
                schemaValidator,
                objectMapper
        );
        when(novelGateway.findChaptersByProjectId(20L)).thenReturn(List.of(chapter(101L), chapter(102L), chapter(103L)));
    }

    @Test
    void should_apply_only_progressions_effective_at_target_chapter() {
        StoryBibleProgression startsAtSecond = progression(201L, 102L, null, "wounded");
        StoryBibleProgression future = progression(202L, 103L, null, "recovered");

        var result = resolver.resolve(20L, 102L, node(), nodeType(), List.of(future, startsAtSecond));

        assertThat(result.complete()).isTrue();
        assertThat(result.appliedProgressionIds()).containsExactly(201L);
        assertThat(result.state().at("/attributes/status").asText()).isEqualTo("wounded");
    }

    @Test
    void should_respect_inclusive_end_anchor_after_chapter_reordering() {
        StoryBibleProgression bounded = progression(201L, 101L, 102L, "wounded");

        var atEnd = resolver.resolve(20L, 102L, node(), nodeType(), List.of(bounded));
        var afterEnd = resolver.resolve(20L, 103L, node(), nodeType(), List.of(bounded));

        assertThat(atEnd.appliedProgressionIds()).containsExactly(201L);
        assertThat(afterEnd.appliedProgressionIds()).isEmpty();
        assertThat(afterEnd.state().at("/attributes/status").asText()).isEqualTo("healthy");
    }

    @Test
    void should_report_unresolved_anchor_without_silently_applying_patch() {
        StoryBibleProgression deletedAnchor = progression(201L, 999L, null, "wounded");

        var result = resolver.resolve(20L, 103L, node(), nodeType(), List.of(deletedAnchor));

        assertThat(result.complete()).isFalse();
        assertThat(result.appliedProgressionIds()).isEmpty();
        assertThat(result.unresolvedAnchors()).extracting(StoryBibleEffectiveStateResolver.UnresolvedAnchor::chapterId)
                .containsExactly(999L);
        assertThat(result.state().at("/attributes/status").asText()).isEqualTo("healthy");
    }

    @Test
    void should_report_same_position_path_collision_and_apply_neither_winner() {
        StoryBibleProgression first = progression(201L, 102L, null, "wounded");
        StoryBibleProgression second = progression(202L, 102L, null, "missing");

        var result = resolver.resolve(20L, 103L, node(), nodeType(), List.of(first, second));

        assertThat(result.complete()).isFalse();
        assertThat(result.appliedProgressionIds()).isEmpty();
        assertThat(result.conflicts()).singleElement().satisfies(conflict -> {
            assertThat(conflict.code()).isEqualTo("SAME_POSITION_PATH_COLLISION");
            assertThat(conflict.progressionIds()).containsExactly(201L, 202L);
        });
        assertThat(result.state().at("/attributes/status").asText()).isEqualTo("healthy");
    }

    private NovelChapter chapter(Long chapterId) {
        NovelChapter chapter = new NovelChapter();
        chapter.setChapterId(chapterId);
        return chapter;
    }

    private StoryBibleNode node() {
        StoryBibleNode node = new StoryBibleNode();
        node.setNodeId(10L);
        node.setTypeId(30L);
        node.setTitle("Mira");
        node.setSummary("Pilot");
        node.setBodyMarkdown("Base setting");
        node.setAttributesJson("{\"status\":\"healthy\"}");
        return node;
    }

    private StoryBibleNodeType nodeType() {
        StoryBibleNodeType type = new StoryBibleNodeType();
        type.setTypeId(30L);
        type.setFieldSchemaJson("""
                {"type":"object","properties":{"status":{"type":"string"}},"required":["status"],"additionalProperties":false}
                """);
        return type;
    }

    private StoryBibleProgression progression(Long id, Long anchor, Long end, String status) {
        StoryBibleProgression progression = new StoryBibleProgression();
        progression.setProgressionId(id);
        progression.setNodeId(10L);
        progression.setAnchorChapterId(anchor);
        progression.setEndChapterId(end);
        progression.setPatchJson("[{\"op\":\"replace\",\"path\":\"/attributes/status\",\"value\":\"" + status + "\"}]");
        return progression;
    }
}
