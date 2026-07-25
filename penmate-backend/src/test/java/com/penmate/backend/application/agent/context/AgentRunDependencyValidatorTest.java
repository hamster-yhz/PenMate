package com.penmate.backend.application.agent.context;

import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import org.junit.jupiter.api.Test;
import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRunDependencyValidatorTest {

    @Test
    void detects_active_chapter_body_change_without_coarse_project_change() {
        ContextEpochSnapshotFactory snapshots = mock(ContextEpochSnapshotFactory.class);
        StoryBibleRoutingPreferenceResolver preferences = mock(StoryBibleRoutingPreferenceResolver.class);
        AgentContextCatalogHashService hashes = mock(AgentContextCatalogHashService.class);
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        AgentContextEpochService epochs = mock(AgentContextEpochService.class);
        when(snapshots.create(10L, 40L)).thenReturn(new ContextEpochSnapshotCodec.Snapshot(
                1, 10L, 20L, 3L, 7L, 40L, 12L, List.of(), List.of()));
        when(preferences.resolve(10L, 30L, 50L)).thenReturn(
                new StoryBibleRoutingPreferenceResolver.EffectivePreference(
                        StoryBibleRoutingMode.RETRIEVAL, null));
        when(hashes.hashes(any(TaskProfile.class))).thenReturn(
                new AgentContextCatalogHashService.Hashes("p", "s", "t"));
        when(sessions.findActiveStyleBindingRevision(30L)).thenReturn(9L);
        var expected = new AgentRunContextArtifactService.DependencyManifest(
                3L, 7L, 40L, 11L, 9L, "RETRIEVAL", null, "p", "s", "t");
        var artifact = new AgentRunContextArtifactService.ResolvedArtifact(
                2, 60L, 70L, null, null, List.of(), expected);

        var result = new AgentRunDependencyValidator(snapshots, preferences, hashes, sessions, epochs)
                .validate(run(), input(), artifact);

        assertThat(result.current()).isFalse();
        assertThat(result.changedFields()).containsExactly("activeChapterContentRevision");
    }

    @Test
    void accepts_identical_field_level_manifest() {
        ContextEpochSnapshotFactory snapshots = mock(ContextEpochSnapshotFactory.class);
        StoryBibleRoutingPreferenceResolver preferences = mock(StoryBibleRoutingPreferenceResolver.class);
        AgentContextCatalogHashService hashes = mock(AgentContextCatalogHashService.class);
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        AgentContextEpochService epochs = mock(AgentContextEpochService.class);
        when(snapshots.create(10L, 40L)).thenReturn(new ContextEpochSnapshotCodec.Snapshot(
                1, 10L, 20L, 3L, 7L, 40L, 11L, List.of(), List.of()));
        when(preferences.resolve(10L, 30L, 50L)).thenReturn(
                new StoryBibleRoutingPreferenceResolver.EffectivePreference(
                        StoryBibleRoutingMode.RETRIEVAL, null));
        when(hashes.hashes(any(TaskProfile.class))).thenReturn(
                new AgentContextCatalogHashService.Hashes("p", "s", "t"));
        when(sessions.findActiveStyleBindingRevision(30L)).thenReturn(9L);
        var manifest = new AgentRunContextArtifactService.DependencyManifest(
                3L, 7L, 40L, 11L, 9L, "RETRIEVAL", null, "p", "s", "t");

        var result = new AgentRunDependencyValidator(snapshots, preferences, hashes, sessions, epochs)
                .validate(run(), input(), new AgentRunContextArtifactService.ResolvedArtifact(
                        2, 60L, 70L, null, null, List.of(), manifest));

        assertThat(result.current()).isTrue();
        assertThat(result.changedFields()).isEmpty();
    }

    private AgentRun run() {
        return new AgentRun(60L, 10L, 30L, 80L, 50L,
                "RUNNING", "executing", 70L, null, 4L, null, "trace", null, null);
    }

    private AgentRunInput input() {
        return new AgentRunInput(60L, "continue", "WRITE", 40L,
                null, null, null, null, "hash");
    }
}
