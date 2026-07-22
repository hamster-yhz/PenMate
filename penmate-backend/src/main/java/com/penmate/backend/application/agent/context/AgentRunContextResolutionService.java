package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;
import com.penmate.backend.application.agent.orchestration.ConversationWindowBuilder;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
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
    private static final int CONVERSATION_WINDOW_TURNS = 8;

    private final StoryBibleRoutingPreferenceResolver preferences;
    private final ContextEpochSnapshotFactory snapshotFactory;
    private final ContextEpochSnapshotCodec snapshotCodec;
    private final AgentContextCatalogHashService hashes;
    private final AgentContextEpochService epochs;
    private final AgentWorkingSetService workingSet;
    private final AgentWorkingSetPromotionService workingSetPromotions;
    private final StoryBibleContextResolver contextResolver;
    private final AgentRunContextArtifactService artifacts;
    private final AgentModelRoutingService modelRouting;
    private final JsonCodec jsonCodec;
    private final AgentSessionRepository sessionRepository;
    private final ConversationWindowBuilder conversationWindows;

    public AgentRunContextResolutionService(
            StoryBibleRoutingPreferenceResolver preferences,
            ContextEpochSnapshotFactory snapshotFactory,
            ContextEpochSnapshotCodec snapshotCodec,
            AgentContextCatalogHashService hashes,
            AgentContextEpochService epochs,
            AgentWorkingSetService workingSet,
            AgentWorkingSetPromotionService workingSetPromotions,
            StoryBibleContextResolver contextResolver,
            AgentRunContextArtifactService artifacts,
            AgentModelRoutingService modelRouting,
            JsonCodec jsonCodec,
            AgentSessionRepository sessionRepository,
            ConversationWindowBuilder conversationWindows
    ) {
        this.preferences = preferences;
        this.snapshotFactory = snapshotFactory;
        this.snapshotCodec = snapshotCodec;
        this.hashes = hashes;
        this.epochs = epochs;
        this.workingSet = workingSet;
        this.workingSetPromotions = workingSetPromotions;
        this.contextResolver = contextResolver;
        this.artifacts = artifacts;
        this.modelRouting = modelRouting;
        this.jsonCodec = jsonCodec;
        this.sessionRepository = sessionRepository;
        this.conversationWindows = conversationWindows;
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
                input.chapterId(), newSnapshot.activeChapterContentRevision(),
                styleBindingRevision == null ? 0L : styleBindingRevision,
                preference.mode().name(), preference.routerModelConfigId(),
                catalogHashes.promptBundleHash(), catalogHashes.skillCatalogHash(),
                catalogHashes.toolCatalogHash(), snapshotJson));
        ContextEpochSnapshotCodec.Snapshot boundSnapshot = snapshotCodec.decode(
                epochs.loadVerifiedSnapshot(binding.epoch().epochId()));
        List<AgentWorkingSetEntry> currentWorkingSet = workingSet.list(run.sessionId());
        List<Long> workingSetIds = currentWorkingSet.stream().map(AgentWorkingSetEntry::nodeId).toList();
        List<AgentLlmMessage> conversationWindow = conversationWindows.buildBeforeTurn(
                run.sessionId(), run.turnId(), CONVERSATION_WINDOW_TURNS);
        AgentLlmExecutionConfig selectorConfig = preference.routerModelConfigId() == null ? executionConfig
                : modelRouting.resolveExecutionConfig(run.ownerUserId(), preference.routerModelConfigId(), traceId);
        var resolved = contextResolver.resolve(new StoryBibleRouteRequest(
                run.projectId(), run.sessionId(), run.runId(), input.chapterId(), input.promptSnapshot(), List.of(),
                preference.mode(), boundSnapshot.storyBibleRevision(), boundSnapshot.selectorCatalog(), workingSetIds,
                selectorConfig, conversationWindow));
        ContextPackage contextPackage = toContextPackage(resolved, boundSnapshot, workingSetIds,
                input.styleSnapshotJson(), input.chapterId());
        var manifest = new AgentRunContextArtifactService.DependencyManifest(
                newSnapshot.storyBibleRevision(), newSnapshot.manuscriptRevision(), input.chapterId(),
                newSnapshot.activeChapterContentRevision(), styleBindingRevision == null ? 0L : styleBindingRevision,
                preference.mode().name(), preference.routerModelConfigId(),
                catalogHashes.promptBundleHash(), catalogHashes.skillCatalogHash(), catalogHashes.toolCatalogHash());
        var durable = new AgentRunContextArtifactService.ResolvedArtifact(
                3, run.runId(), binding.epoch().epochId(), resolved.decision(), contextPackage, workingSetIds, manifest,
                progressionIds(resolved), contentHashes(resolved));
        var ref = artifacts.save(run.runId(), durable);
        return new Resolution(binding, contextPackage, resolved.decision(), ref, catalogHashes,
                sha256(snapshotCodec.encode(new ContextEpochSnapshotCodec.Snapshot(
                        newSnapshot.schemaVersion(), newSnapshot.projectId(), newSnapshot.storyBibleId(),
                        newSnapshot.storyBibleRevision(), newSnapshot.manuscriptRevision(), newSnapshot.activeChapterId(),
                        newSnapshot.activeChapterContentRevision(), newSnapshot.coreContext(), List.of()))),
                conversationWindow);
    }

    public AgentWorkingSetPromotionService.PromotionSummary promoteAfterDurable(
            Long sessionId, Long turnId, List<Long> nodeIds) {
        return workingSetPromotions.promoteBestEffort(sessionId, turnId, nodeIds, BigDecimal.ONE);
    }

    private List<Long> progressionIds(StoryBibleContextResolver.ResolvedContext resolved) {
        return resolved.nodes().stream().flatMap(node -> node.appliedProgressionIds().stream()).distinct().toList();
    }

    private java.util.Map<Long, String> contentHashes(StoryBibleContextResolver.ResolvedContext resolved) {
        java.util.Map<Long, String> hashes = new java.util.LinkedHashMap<>();
        resolved.nodes().forEach(node -> hashes.put(node.nodeId(), sha256(json(node))));
        return java.util.Map.copyOf(hashes);
    }

    private ContextPackage toContextPackage(StoryBibleContextResolver.ResolvedContext resolved,
                                            ContextEpochSnapshotCodec.Snapshot snapshot,
                                            List<Long> workingSetIds, String styleSnapshot, Long chapterId) {
        java.util.Set<Long> coreIds = snapshot.coreContext().stream()
                .map(ContextEpochSnapshotCodec.CoreNode::nodeId).collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> selectedIds = new java.util.HashSet<>(resolved.decision().selectedNodeIds());
        java.util.Set<Long> workingIds = new java.util.HashSet<>(workingSetIds);
        List<String> rendered = new ArrayList<>();
        List<String> core = snapshot.coreContext().stream().map(this::json).toList();
        List<String> working = new ArrayList<>();
        List<String> selected = new ArrayList<>();
        for (StoryBibleContextResolver.RenderedNode node : resolved.nodes()) {
            String json = json(node);
            rendered.add(json);
            if (!coreIds.contains(node.nodeId()) && workingIds.contains(node.nodeId())) working.add(json);
            if (selectedIds.contains(node.nodeId()) && !coreIds.contains(node.nodeId())) selected.add(json);
        }
        return new ContextPackage(List.of("story-bible"), resolved.decision().missingFlags(), List.of(), rendered,
                core, working, selected, List.of(), styleSnapshot,
                chapterId == null ? "" : "chapter:" + chapterId);
    }

    private String json(Object value) {
        try { return jsonCodec.write(value); }
        catch (RuntimeException ex) { throw new IllegalStateException("Failed to render resolved Story Bible node", ex); }
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
            String storyBibleCoreHash,
            List<AgentLlmMessage> conversationWindow
    ) {
        public Resolution {
            conversationWindow = List.copyOf(conversationWindow == null ? List.of() : conversationWindow);
        }

        public Resolution(AgentContextEpochService.Binding epochBinding, ContextPackage contextPackage,
                          StoryBibleRouteDecision routeDecision,
                          AgentRunContextArtifactService.ArtifactRef artifactRef,
                          AgentContextCatalogHashService.Hashes catalogHashes,
                          String storyBibleCoreHash) {
            this(epochBinding, contextPackage, routeDecision, artifactRef, catalogHashes, storyBibleCoreHash, List.of());
        }
    }
}
