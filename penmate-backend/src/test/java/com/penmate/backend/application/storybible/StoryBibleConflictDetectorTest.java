package com.penmate.backend.application.storybible;

import com.penmate.backend.application.storybible.StoryBibleConflictDetector.ProgressionPatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoryBibleConflictDetectorTest {

    private final StoryBibleConflictDetector detector = new StoryBibleConflictDetector();

    @Test
    void should_only_conflict_for_distinct_progressions_at_same_position_and_path() {
        var conflicts = detector.detect(List.of(
                new ProgressionPatch(10L, 2, List.of("/attributes/status", "/summary")),
                new ProgressionPatch(11L, 2, List.of("/attributes/status")),
                new ProgressionPatch(12L, 3, List.of("/attributes/status"))
        ));

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.getFirst().path()).isEqualTo("/attributes/status");
        assertThat(conflicts.getFirst().progressionIds()).containsExactly(10L, 11L);
    }
}
