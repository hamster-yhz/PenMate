package com.penmate.backend.application.agent.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.context.model.AgentContextEpoch;
import com.penmate.backend.domain.agent.context.repository.AgentContextEpochRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@Service
public class AgentContextEpochService {

    private final AgentContextEpochRepository repository;
    private final BusinessIdGenerator idGenerator;
    private final ObjectStorageService objectStorage;
    private final ObjectMapper objectMapper;
    private final ContextEpochSnapshotCache cache;

    public AgentContextEpochService(AgentContextEpochRepository repository, BusinessIdGenerator idGenerator,
                                    ObjectStorageService objectStorage, ObjectMapper objectMapper,
                                    ContextEpochSnapshotCache cache) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
        this.objectStorage = Objects.requireNonNull(objectStorage, "objectStorage");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    @Transactional
    public Binding bind(BindRequest request) {
        Objects.requireNonNull(request, "request");
        String fingerprint = fingerprint(request);
        if (repository.lockSession(request.sessionId()) == null) {
            throw BusinessException.notFound("Agent session not found");
        }
        AgentContextEpoch current = repository.findCurrentByFingerprint(request.sessionId(), fingerprint);
        if (current != null) {
            requireBound(repository.bindRun(request.runId(), current.epochId()), "Run already belongs to another Context Epoch");
            return new Binding(current, true);
        }

        long epochId = idGenerator.nextId();
        int epochNo = repository.nextEpochNo(request.sessionId());
        byte[] snapshot = required(request.snapshotJson(), "snapshotJson").getBytes(StandardCharsets.UTF_8);
        String snapshotHash = sha256(snapshot);
        String objectKey = "agent-context-epochs/" + request.sessionId() + "/" + epochNo + "-" + epochId + ".json";
        String snapshotText = new String(snapshot, StandardCharsets.UTF_8);
        ObjectStorageService.PutObjectResult stored = objectStorage.putText(objectKey, snapshotText, "application/json");
        if (stored.size() == null || stored.size() != snapshot.length) {
            throw BusinessException.of("Context Epoch snapshot upload size mismatch");
        }
        String storedSnapshot = objectStorage.readText(objectKey);
        if (storedSnapshot == null
                || !MessageDigest.isEqual(snapshot, storedSnapshot.getBytes(StandardCharsets.UTF_8))) {
            throw BusinessException.of("Context Epoch snapshot upload verification failed");
        }
        AgentContextEpoch epoch = new AgentContextEpoch(
                epochId, request.sessionId(), epochNo, fingerprint, request.storyBibleRevision(),
                request.manuscriptRevision(), request.activeChapterId(), request.activeChapterContentRevision(),
                request.styleBindingRevision(),
                required(request.routingMode(), "routingMode"), request.routerModelConfigId(),
                required(request.promptBundleHash(), "promptBundleHash"),
                required(request.skillCatalogHash(), "skillCatalogHash"), required(request.toolCatalogHash(), "toolCatalogHash"),
                objectKey, snapshotHash, (long) snapshot.length, null, null
        );
        requireOne(repository.insert(epoch), "Failed to persist Context Epoch");
        repository.supersedeCurrent(request.sessionId(), epochId);
        requireOne(repository.bindSession(request.sessionId(), epochId), "Agent session not found");
        requireBound(repository.bindRun(request.runId(), epochId), "Run already belongs to another Context Epoch");
        cache.put(epochId, snapshotText);
        return new Binding(epoch, false);
    }

    public String loadVerifiedSnapshot(Long epochId) {
        AgentContextEpoch epoch = repository.findById(epochId);
        if (epoch == null) throw BusinessException.notFound("Context Epoch not found");
        String cached = cache.get(epochId);
        if (cached != null && matches(epoch, cached.getBytes(StandardCharsets.UTF_8))) return cached;
        String snapshot = objectStorage.readText(epoch.snapshotObjectKey());
        byte[] content = snapshot.getBytes(StandardCharsets.UTF_8);
        if (!matches(epoch, content)) {
            throw BusinessException.conflict("Context Epoch snapshot integrity check failed");
        }
        cache.put(epochId, snapshot);
        return snapshot;
    }

    private boolean matches(AgentContextEpoch epoch, byte[] content) {
        return content.length == epoch.snapshotSizeBytes() && sha256(content).equals(epoch.snapshotHash());
    }

    private String fingerprint(BindRequest request) {
        Map<String, Object> fields = new TreeMap<>();
        fields.put("sessionId", request.sessionId());
        fields.put("storyBibleRevision", request.storyBibleRevision());
        fields.put("manuscriptRevision", request.manuscriptRevision());
        fields.put("activeChapterId", request.activeChapterId());
        fields.put("activeChapterContentRevision", request.activeChapterContentRevision());
        fields.put("styleBindingRevision", request.styleBindingRevision());
        fields.put("routingMode", request.routingMode());
        fields.put("routerModelConfigId", request.routerModelConfigId());
        fields.put("promptBundleHash", request.promptBundleHash());
        fields.put("skillCatalogHash", request.skillCatalogHash());
        fields.put("toolCatalogHash", request.toolCatalogHash());
        try {
            return sha256(objectMapper.writeValueAsBytes(fields));
        } catch (JsonProcessingException ex) {
            throw BusinessException.of("Failed to compute Context Epoch fingerprint");
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw BusinessException.badRequest(field + " is required");
        return value.trim();
    }

    private void requireOne(int affected, String message) {
        if (affected != 1) throw BusinessException.of(message);
    }

    private void requireBound(int affected, String message) {
        if (affected != 1) throw BusinessException.conflict(message);
    }

    public record BindRequest(
            Long sessionId,
            Long runId,
            Long storyBibleRevision,
            Long manuscriptRevision,
            Long activeChapterId,
            Long activeChapterContentRevision,
            Long styleBindingRevision,
            String routingMode,
            Long routerModelConfigId,
            String promptBundleHash,
            String skillCatalogHash,
            String toolCatalogHash,
            String snapshotJson
    ) {
        public BindRequest(Long sessionId, Long runId, Long storyBibleRevision, Long manuscriptRevision,
                           Long activeChapterId, Long styleBindingRevision, String routingMode,
                           Long routerModelConfigId, String promptBundleHash,
                           String skillCatalogHash, String toolCatalogHash, String snapshotJson) {
            this(sessionId, runId, storyBibleRevision, manuscriptRevision, activeChapterId, 0L,
                    styleBindingRevision, routingMode, routerModelConfigId, promptBundleHash,
                    skillCatalogHash, toolCatalogHash, snapshotJson);
        }
    }

    public AgentContextEpoch get(Long epochId) {
        AgentContextEpoch epoch = repository.findById(epochId);
        if (epoch == null) throw BusinessException.notFound("Context Epoch not found");
        return epoch;
    }

    public record Binding(AgentContextEpoch epoch, boolean reused) {
    }
}
