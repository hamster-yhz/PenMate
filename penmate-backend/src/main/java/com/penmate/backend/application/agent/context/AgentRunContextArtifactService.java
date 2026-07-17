package com.penmate.backend.application.agent.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.run.model.AgentArtifact;
import com.penmate.backend.domain.agent.run.repository.AgentArtifactRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import com.penmate.backend.application.agent.prompt.PromptPlan;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.ListIterator;

@Service
public class AgentRunContextArtifactService {
    private final AgentArtifactRepository artifacts;
    private final BusinessIdGenerator ids;
    private final ObjectStorageService storage;
    private final ObjectMapper objectMapper;

    public AgentRunContextArtifactService(AgentArtifactRepository artifacts, BusinessIdGenerator ids,
                                          ObjectStorageService storage, ObjectMapper objectMapper) {
        this.artifacts = artifacts;
        this.ids = ids;
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

    public ArtifactRef save(Long runId, ResolvedArtifact artifact) {
        String json = json(artifact);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        Long artifactId = ids.nextId();
        String key = "agent-runs/" + runId + "/context-" + artifactId + ".json";
        ObjectStorageService.PutObjectResult stored = storage.putText(key, json, "application/json");
        if (stored.size() == null || stored.size() != bytes.length) {
            throw BusinessException.of("Run context artifact upload size mismatch");
        }
        verifyUploadedText(key, bytes, hash, "Run context artifact");
        ArtifactRef ref = new ArtifactRef(artifactId, key, hash, bytes.length);
        artifacts.save(new AgentArtifact(artifactId, runId, null, "context.resolved", json(ref), bytes.length, null));
        return ref;
    }

    public ResolvedArtifact load(Long artifactId) {
        AgentArtifact row = artifacts.findById(artifactId);
        if (row == null || !"context.resolved".equals(row.artifactType())) {
            throw BusinessException.notFound("Run context artifact not found");
        }
        ArtifactRef ref;
        try { ref = objectMapper.readValue(row.payloadJson(), ArtifactRef.class); }
        catch (JsonProcessingException ex) { throw BusinessException.conflict("Run context artifact metadata is invalid"); }
        String json = storage.readText(ref.objectKey());
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length != ref.sizeBytes() || !sha256(bytes).equals(ref.sha256())) {
            throw BusinessException.conflict("Run context artifact integrity check failed");
        }
        try { return objectMapper.readValue(json, ResolvedArtifact.class); }
        catch (JsonProcessingException ex) { throw BusinessException.conflict("Run context artifact is invalid"); }
    }

    public ResolvedArtifact loadContextForRun(Long runId, List<Long> artifactRefs) {
        List<Long> refs = artifactRefs == null ? List.of() : artifactRefs;
        ListIterator<Long> iterator = refs.listIterator(refs.size());
        while (iterator.hasPrevious()) {
            Long artifactId = iterator.previous();
            AgentArtifact row = artifacts.findById(artifactId);
            if (row != null && runId.equals(row.runId()) && "context.resolved".equals(row.artifactType())) {
                return load(artifactId);
            }
        }
        throw BusinessException.notFound("Run context artifact not found");
    }

    public ResolvedArtifact loadLatestContextForRun(Long runId) {
        AgentArtifact row = artifacts.findLatest(runId, "context.resolved");
        if (row == null) {
            throw BusinessException.notFound("Run context artifact not found");
        }
        return load(row.artifactId());
    }

    public ArtifactRef savePromptPlan(Long runId, PromptPlan plan, PromptManifest manifest) {
        String json = json(new PromptArtifact(1, plan, manifest));
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        Long artifactId = ids.nextId();
        String key = "agent-runs/" + runId + "/prompt-" + artifactId + ".json";
        ObjectStorageService.PutObjectResult stored = storage.putText(key, json, "application/json");
        if (stored.size() == null || stored.size() != bytes.length) {
            throw BusinessException.of("Run prompt artifact upload size mismatch");
        }
        verifyUploadedText(key, bytes, hash, "Run prompt artifact");
        ArtifactRef ref = new ArtifactRef(artifactId, key, hash, bytes.length);
        artifacts.save(new AgentArtifact(artifactId, runId, null, "prompt.composed", json(ref), bytes.length, null));
        return ref;
    }

    public PromptArtifact loadPromptPlan(Long artifactId) {
        AgentArtifact row = artifacts.findById(artifactId);
        if (row == null || !"prompt.composed".equals(row.artifactType())) {
            throw BusinessException.notFound("Run prompt artifact not found");
        }
        ArtifactRef ref;
        try { ref = objectMapper.readValue(row.payloadJson(), ArtifactRef.class); }
        catch (JsonProcessingException ex) { throw BusinessException.conflict("Run prompt artifact metadata is invalid"); }
        String json = storage.readText(ref.objectKey());
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length != ref.sizeBytes() || !sha256(bytes).equals(ref.sha256())) {
            throw BusinessException.conflict("Run prompt artifact integrity check failed");
        }
        try { return objectMapper.readValue(json, PromptArtifact.class); }
        catch (JsonProcessingException ex) { throw BusinessException.conflict("Run prompt artifact is invalid"); }
    }

    public PromptArtifact loadPromptPlanForRun(Long runId, List<Long> artifactRefs) {
        List<Long> refs = artifactRefs == null ? List.of() : artifactRefs;
        ListIterator<Long> iterator = refs.listIterator(refs.size());
        while (iterator.hasPrevious()) {
            Long artifactId = iterator.previous();
            AgentArtifact row = artifacts.findById(artifactId);
            if (row != null && runId.equals(row.runId()) && "prompt.composed".equals(row.artifactType())) {
                return loadPromptPlan(artifactId);
            }
        }
        throw BusinessException.notFound("Run prompt artifact not found");
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw BusinessException.of("Failed to serialize Run context artifact"); }
    }

    private void verifyUploadedText(String objectKey, byte[] expected, String expectedHash, String label) {
        String readBack = storage.readText(objectKey);
        if (readBack == null) {
            throw BusinessException.of(label + " upload verification failed");
        }
        byte[] actual = readBack.getBytes(StandardCharsets.UTF_8);
        if (actual.length != expected.length || !sha256(actual).equals(expectedHash)) {
            throw BusinessException.of(label + " upload verification failed");
        }
    }

    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }

    public record ArtifactRef(Long artifactId, String objectKey, String sha256, int sizeBytes) {
    }
    public record ResolvedArtifact(
            int schemaVersion,
            Long runId,
            Long contextEpochId,
            StoryBibleRouteDecision routeDecision,
            ContextPackage contextPackage,
            java.util.List<Long> workingSetNodeIds,
            DependencyManifest dependencies
    ) {
        public ResolvedArtifact(int schemaVersion, Long runId, Long contextEpochId,
                                StoryBibleRouteDecision routeDecision, ContextPackage contextPackage,
                                java.util.List<Long> workingSetNodeIds) {
            this(schemaVersion, runId, contextEpochId, routeDecision, contextPackage, workingSetNodeIds, null);
        }
        public ResolvedArtifact {
            workingSetNodeIds = java.util.List.copyOf(workingSetNodeIds == null ? java.util.List.of() : workingSetNodeIds);
        }
    }
    public record DependencyManifest(
            Long storyBibleRevision,
            Long projectStructureRevision,
            Long activeChapterId,
            Long activeChapterContentRevision,
            Long styleBindingRevision,
            String routingMode,
            Long routerModelConfigId,
            Long routerModelConfigRevision,
            String promptBundleHash,
            String skillCatalogHash,
            String toolCatalogHash
    ) {
    }
    public record PromptArtifact(int schemaVersion, PromptPlan plan, PromptManifest manifest) {
    }
    public record PromptManifest(Long contextEpochId, String promptBundleHash, String toolCatalogHash,
                                 String skillCatalogHash, String storyBibleCoreHash,
                                 String stablePrefixHash, String dynamicContextHash) {
    }
}
