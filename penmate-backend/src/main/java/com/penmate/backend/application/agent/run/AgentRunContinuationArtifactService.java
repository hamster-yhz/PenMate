package com.penmate.backend.application.agent.run;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.run.model.AgentArtifact;
import com.penmate.backend.domain.agent.run.model.AgentRunContinuation;
import com.penmate.backend.domain.agent.run.repository.AgentArtifactRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

@Service
public class AgentRunContinuationArtifactService {
    public static final String ARTIFACT_TYPE = "llm.continuation";

    private final AgentArtifactRepository artifacts;
    private final BusinessIdGenerator ids;
    private final ObjectStorageService storage;
    private final ObjectMapper objectMapper;

    public AgentRunContinuationArtifactService(AgentArtifactRepository artifacts,
                                               BusinessIdGenerator ids,
                                               ObjectStorageService storage,
                                               ObjectMapper objectMapper) {
        this.artifacts = artifacts;
        this.ids = ids;
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

    public ArtifactRef save(AgentRunContinuation continuation) {
        String json = json(continuation);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        String hash = sha256(bytes);
        Long artifactId = ids.nextId();
        String key = "agent-runs/" + continuation.runId() + "/continuations/" + artifactId + ".json";
        ObjectStorageService.PutObjectResult stored = storage.putText(key, json, "application/json");
        if (stored == null || stored.size() == null || stored.size() != bytes.length) {
            throw BusinessException.of("Agent Run continuation upload size mismatch");
        }
        byte[] verified = storage.readBytes(key);
        if (verified == null || verified.length != bytes.length || !hash.equals(sha256(verified))) {
            throw BusinessException.of("Agent Run continuation upload integrity check failed");
        }
        ArtifactRef ref = new ArtifactRef(artifactId, key, hash, bytes.length);
        artifacts.save(new AgentArtifact(artifactId, continuation.runId(), null, ARTIFACT_TYPE,
                json(ref), bytes.length, null));
        return ref;
    }

    public AgentRunContinuation load(Long artifactId) {
        AgentArtifact row = artifacts.findById(artifactId);
        if (row == null || !ARTIFACT_TYPE.equals(row.artifactType())) {
            throw BusinessException.notFound("Agent Run continuation artifact not found");
        }
        ArtifactRef ref;
        try {
            ref = objectMapper.readValue(row.payloadJson(), ArtifactRef.class);
        } catch (JsonProcessingException ex) {
            throw BusinessException.conflict("Agent Run continuation metadata is invalid");
        }
        byte[] bytes = storage.readBytes(ref.objectKey());
        if (bytes.length != ref.sizeBytes() || !sha256(bytes).equals(ref.sha256())) {
            throw BusinessException.conflict("Agent Run continuation integrity check failed");
        }
        try {
            return objectMapper.readValue(bytes, AgentRunContinuation.class);
        } catch (Exception ex) {
            throw BusinessException.conflict("Agent Run continuation is invalid");
        }
    }

    public Optional<AgentRunContinuation> loadLatestForRun(Long runId, List<Long> artifactRefs) {
        List<Long> refs = artifactRefs == null ? List.of() : artifactRefs;
        ListIterator<Long> iterator = refs.listIterator(refs.size());
        while (iterator.hasPrevious()) {
            Long artifactId = iterator.previous();
            AgentArtifact row = artifacts.findById(artifactId);
            if (row != null && runId.equals(row.runId()) && ARTIFACT_TYPE.equals(row.artifactType())) {
                AgentRunContinuation continuation = load(artifactId);
                if (!runId.equals(continuation.runId())) {
                    throw BusinessException.conflict("Agent Run continuation belongs to another Run");
                }
                return Optional.of(continuation);
            }
        }
        return Optional.empty();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw BusinessException.of("Failed to serialize Agent Run continuation");
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public record ArtifactRef(Long artifactId, String objectKey, String sha256, int sizeBytes) {
    }
}
