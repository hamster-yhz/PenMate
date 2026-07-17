package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class AgentRunDependencyValidator {

    private final ContextEpochSnapshotFactory snapshots;
    private final StoryBibleRoutingPreferenceResolver preferences;
    private final AgentContextCatalogHashService catalogHashes;
    private final AgentSessionRepository sessions;
    private final AgentContextEpochService epochs;

    public AgentRunDependencyValidator(ContextEpochSnapshotFactory snapshots,
                                       StoryBibleRoutingPreferenceResolver preferences,
                                       AgentContextCatalogHashService catalogHashes,
                                       AgentSessionRepository sessions,
                                       AgentContextEpochService epochs) {
        this.snapshots = snapshots;
        this.preferences = preferences;
        this.catalogHashes = catalogHashes;
        this.sessions = sessions;
        this.epochs = epochs;
    }

    public Validation validate(AgentRun run, AgentRunInput input,
                               AgentRunContextArtifactService.ResolvedArtifact artifact) {
        var snapshot = snapshots.create(run.projectId(), input.chapterId());
        var preference = preferences.resolve(run.projectId(), run.sessionId(), run.ownerUserId());
        var hashes = catalogHashes.hashes(TaskProfile.fromTaskType(input.taskType()).executionProfile());
        Long styleRevision = sessions.findActiveStyleBindingRevision(run.sessionId());
        var current = new AgentRunContextArtifactService.DependencyManifest(
                snapshot.storyBibleRevision(), snapshot.manuscriptRevision(), input.chapterId(),
                snapshot.activeChapterContentRevision(), styleRevision == null ? 0L : styleRevision,
                preference.mode().name(), preference.routerModelConfigId(), preference.routerModelConfigRevision(),
                hashes.promptBundleHash(), hashes.skillCatalogHash(), hashes.toolCatalogHash());
        var expected = artifact.dependencies() == null ? fromEpoch(artifact.contextEpochId()) : artifact.dependencies();
        List<String> changed = differences(expected, current);
        return new Validation(changed.isEmpty(), expected, current, changed);
    }

    private AgentRunContextArtifactService.DependencyManifest fromEpoch(Long epochId) {
        var epoch = epochs.get(epochId);
        return new AgentRunContextArtifactService.DependencyManifest(
                epoch.storyBibleRevision(), epoch.manuscriptRevision(), epoch.activeChapterId(),
                epoch.activeChapterContentRevision(), epoch.styleBindingRevision(), epoch.routingMode(),
                epoch.routerModelConfigId(), epoch.routerModelConfigRevision(), epoch.promptBundleHash(),
                epoch.skillCatalogHash(), epoch.toolCatalogHash());
    }

    private List<String> differences(AgentRunContextArtifactService.DependencyManifest expected,
                                     AgentRunContextArtifactService.DependencyManifest current) {
        List<String> changed = new ArrayList<>();
        compare(changed, "storyBibleRevision", expected.storyBibleRevision(), current.storyBibleRevision());
        compare(changed, "projectStructureRevision", expected.projectStructureRevision(), current.projectStructureRevision());
        compare(changed, "activeChapterId", expected.activeChapterId(), current.activeChapterId());
        compare(changed, "activeChapterContentRevision", expected.activeChapterContentRevision(), current.activeChapterContentRevision());
        compare(changed, "styleBindingRevision", expected.styleBindingRevision(), current.styleBindingRevision());
        compare(changed, "routingMode", expected.routingMode(), current.routingMode());
        compare(changed, "routerModelConfigId", expected.routerModelConfigId(), current.routerModelConfigId());
        compare(changed, "routerModelConfigRevision", expected.routerModelConfigRevision(), current.routerModelConfigRevision());
        compare(changed, "promptBundleHash", expected.promptBundleHash(), current.promptBundleHash());
        compare(changed, "skillCatalogHash", expected.skillCatalogHash(), current.skillCatalogHash());
        compare(changed, "toolCatalogHash", expected.toolCatalogHash(), current.toolCatalogHash());
        return List.copyOf(changed);
    }

    private void compare(List<String> changed, String field, Object expected, Object current) {
        if (!Objects.equals(expected, current)) changed.add(field);
    }

    public record Validation(boolean current,
                             AgentRunContextArtifactService.DependencyManifest expected,
                             AgentRunContextArtifactService.DependencyManifest actual,
                             List<String> changedFields) {
    }
}
