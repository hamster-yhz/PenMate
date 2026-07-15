package com.penmate.backend.application.agent.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;
import com.penmate.backend.domain.agent.context.model.AgentWorkingSetEntry;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class AgentRunContextResolutionService {
    private final StoryBibleRoutingPreferenceResolver preferences;
    private final ContextEpochSnapshotFactory snapshotFactory;
    private final ContextEpochSnapshotCodec snapshotCodec;
    private final AgentContextCatalogHashService hashes;
    private final AgentContextEpochService epochs;
    private final AgentWorkingSetService workingSet;
    private final StoryBibleContextResolver contextResolver;
    private final AgentRunContextArtifactService artifacts;
    private final AgentModelRoutingService modelRouting;
    private final ObjectMapper objectMapper;
    private final AgentSessionRepository sessionRepository;

    public AgentRunContextResolutionService(
            StoryBibleRoutingPreferenceResolver preferences,
            ContextEpochSnapshotFactory snapshotFactory,
            ContextEpochSnapshotCodec snapshotCodec,
            AgentContextCatalogHashService hashes,
            AgentContextEpochService epochs,
            AgentWorkingSetService workingSet,
            StoryBibleContextResolver contextResolver,
            AgentRunContextArtifactService artifacts,
            AgentModelRoutingService modelRouting,
            ObjectMapper objectMapper,
            AgentSessionRepository sessionRepository
    ) {
        this.preferences = preferences;
        this.snapshotFactory = snapshotFactory;
        this.snapshotCodec = snapshotCodec;
        this.hashes = hashes;
        this.epochs = epochs;
        this.workingSet = workingSet;
        this.contextResolver = contextResolver;
        this.artifacts = artifacts;
        this.modelRouting = modelRouting;
        this.objectMapper = objectMapper;
        this.sessionRepository = sessionRepository;
    }

    public Resolution resolveInitial(AgentRun run, AgentRunInput input, TaskProfile profile,
                                     AgentLlmExecutionConfig executionConfig, String traceId) {
        var preference = preferences.resolve(run.projectId(), run.sessionId(), run.ownerUserId());
        var newSnapshot = snapshotFactory.create(run.projectId(), input.chapterId());
        var catalogHashes = hashes.hashes(profile.executionProfile());
        String snapshotJson = snapshotCodec.encode(newSnapshot);
        Long styleBindingRevision = sessionRepository.findActiveStyleBindingRevision(run.sessionId());
        var binding = epochs.bind(new AgentContextEpochService.BindRequest(
                run.sessionId(), run.runId(), newSnapshot.storyBibleRevision(), newSnapshot.manuscriptRevision(),
                input.chapterId(), styleBindingRevision == null ? 0L : styleBindingRevision,
                preference.mode().name(), preference.routerModelConfigId(),
                preference.routerModelConfigRevision(), catalogHashes.promptBundleHash(), catalogHashes.skillCatalogHash(),
                catalogHashes.toolCatalogHash(), snapshotJson));
        ContextEpochSnapshotCodec.Snapshot boundSnapshot = snapshotCodec.decode(
                epochs.loadVerifiedSnapshot(binding.epoch().epochId()));
        List<AgentWorkingSetEntry> currentWorkingSet = workingSet.list(run.sessionId());
        List<Long> workingSetIds = currentWorkingSet.stream().map(AgentWorkingSetEntry::nodeId).toList();
        AgentLlmExecutionConfig selectorConfig = preference.routerModelConfigId() == null ? executionConfig
                : modelRouting.resolveExecutionConfig(run.ownerUserId(), preference.routerModelConfigId(), traceId);
        var resolved = contextResolver.resolve(new StoryBibleRouteRequest(
                run.projectId(), run.sessionId(), run.runId(), input.chapterId(), input.promptSnapshot(), List.of(),
                preference.mode(), boundSnapshot.storyBibleRevision(), boundSnapshot.selectorCatalog(), workingSetIds, selectorConfig));
        ContextPackage contextPackage = toContextPackage(resolved, boundSnapshot, workingSetIds,
                input.styleSnapshotJson(), input.chapterId());
        var durable = new AgentRunContextArtifactService.ResolvedArtifact(
                1, run.runId(), binding.epoch().epochId(), resolved.decision(), contextPackage, workingSetIds);
        var ref = artifacts.save(run.runId(), durable);
        return new Resolution(binding, contextPackage, resolved.decision(), ref, catalogHashes,
                sha256(snapshotCodec.encode(new ContextEpochSnapshotCodec.Snapshot(
                        newSnapshot.schemaVersion(), newSnapshot.projectId(), newSnapshot.storyBibleId(),
                        newSnapshot.storyBibleRevision(), newSnapshot.manuscriptRevision(), newSnapshot.activeChapterId(),
                        newSnapshot.coreContext(), List.of()))));
    }

    public void promoteAfterDurable(Long sessionId, Long turnId, List<Long> nodeIds) {
        workingSet.promote(sessionId, turnId, nodeIds, BigDecimal.ONE);
    }

    private ContextPackage toContextPackage(StoryBibleContextResolver.ResolvedContext resolved,
                                            ContextEpochSnapshotCodec.Snapshot snapshot,
                                            List<Long> workingSetIds, String styleSnapshot, Long chapterId) {
        java.util.Set<Long> coreIds = snapshot.coreContext().stream()
                .map(ContextEpochSnapshotCodec.CoreNode::nodeId).collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> selectedIds = new java.util.HashSet<>(resolved.decision().selectedNodeIds());
        java.util.Set<Long> workingIds = new java.util.HashSet<>(workingSetIds);
        List<String> rendered = new ArrayList<>();
        List<String> core = new ArrayList<>();
        List<String> working = new ArrayList<>();
        List<String> selected = new ArrayList<>();
        for (StoryBibleContextResolver.RenderedNode node : resolved.nodes()) {
            String json = json(node);
            rendered.add(json);
            if (coreIds.contains(node.nodeId())) core.add(json);
            else if (workingIds.contains(node.nodeId())) working.add(json);
            if (selectedIds.contains(node.nodeId()) && !coreIds.contains(node.nodeId())) selected.add(json);
        }
        return new ContextPackage(List.of("story-bible"), resolved.decision().missingFlags(), List.of(), rendered,
                core, working, selected, List.of(), styleSnapshot,
                chapterId == null ? "" : "chapter:" + chapterId);
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Failed to render resolved Story Bible node", ex); }
    }

    private String sha256(String value) {
        try {
            byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public record Resolution(
            AgentContextEpochService.Binding epochBinding,
            ContextPackage contextPackage,
            StoryBibleRouteDecision routeDecision,
            AgentRunContextArtifactService.ArtifactRef artifactRef,
            AgentContextCatalogHashService.Hashes catalogHashes,
            String storyBibleCoreHash
    ) {
    }
}
