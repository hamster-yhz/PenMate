package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.domain.agent.run.model.AgentCheckpoint;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRuntimeState;
import com.penmate.backend.domain.agent.run.repository.AgentCheckpointRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@Service
public class AgentCheckpointService {

    private static final Logger log = LoggerFactory.getLogger(AgentCheckpointService.class);
    private static final int INLINE_STATE_LIMIT_BYTES = 256 * 1024;
    private static final long REDIS_TTL_SECONDS = 30 * 60;
    private static final int STATE_SCHEMA_VERSION = 2;
    private static final int CHECKPOINTS_TO_KEEP = 2;

    private final AgentCheckpointRepository checkpointRepository;
    private final AgentCheckpointCache checkpointCache;
    private final BusinessIdGenerator businessIdGenerator;
    private final JsonCodec jsonCodec;
    private final ObjectStorageService objectStorage;

    public AgentCheckpointService(AgentCheckpointRepository checkpointRepository,
                                  AgentCheckpointCache checkpointCache,
                                  BusinessIdGenerator businessIdGenerator,
                                  JsonCodec jsonCodec,
                                  ObjectStorageService objectStorage) {
        this.checkpointRepository = checkpointRepository;
        this.checkpointCache = checkpointCache;
        this.businessIdGenerator = businessIdGenerator;
        this.jsonCodec = jsonCodec;
        this.objectStorage = objectStorage;
    }

    public void checkpointIfNeeded(AgentEvent event, AgentRuntimeState state) {
        if (!shouldCheckpoint(event, state)) {
            return;
        }
        AgentCheckpoint latest = checkpointRepository.findLatest(event.runId());
        long checkpointNo = latest == null ? 1L : latest.checkpointNo() + 1L;
        String serializedState = serializeState(state);
        byte[] stateBytes = serializedState.getBytes(StandardCharsets.UTF_8);
        int stateSizeBytes = stateBytes.length;
        String stateSha256 = sha256(stateBytes);
        String stateObjectKey = null;
        String stateJson = serializedState;
        if (stateSizeBytes > INLINE_STATE_LIMIT_BYTES) {
            stateObjectKey = "agent-runs/" + event.runId() + "/checkpoints/"
                    + checkpointNo + "-" + event.sequence() + ".json";
            ObjectStorageService.PutObjectResult stored = objectStorage.putText(
                    stateObjectKey, serializedState, "application/json");
            if (stored == null || stored.size() == null || stored.size() != stateSizeBytes) {
                throw new IllegalStateException("Agent checkpoint object upload size mismatch");
            }
            byte[] verified = objectStorage.readBytes(stateObjectKey);
            if (verified.length != stateSizeBytes || !stateSha256.equals(sha256(verified))) {
                throw new IllegalStateException("Agent checkpoint object integrity check failed");
            }
            stateJson = "{\"externalState\":true}";
        }
        AgentCheckpoint checkpoint = new AgentCheckpoint(
                businessIdGenerator.nextId(),
                event.runId(),
                checkpointNo,
                event.sequence(),
                stateJson,
                stateSizeBytes,
                STATE_SCHEMA_VERSION,
                stateSha256,
                stateObjectKey,
                null
        );
        checkpointRepository.save(checkpoint);
        checkpointRepository.deleteOlderThanLatest(event.runId(), CHECKPOINTS_TO_KEEP);
        try {
            checkpointCache.put(event.runId(), serializedState, Duration.ofSeconds(REDIS_TTL_SECONDS));
        } catch (Exception ex) {
            log.warn("Failed to write checkpoint hot cache: runId={}, checkpointNo={}", event.runId(), checkpointNo, ex);
        }
    }

    public AgentRuntimeState loadLatest(Long runId) {
        try {
            String stateJson = checkpointCache.get(runId);
            if (stateJson != null && !stateJson.isBlank()) {
                return jsonCodec.read(stateJson, AgentRuntimeState.class);
            }
        } catch (Exception ex) {
            log.warn("Failed to load checkpoint hot cache: runId={}", runId, ex);
        }
        for (AgentCheckpoint checkpoint : checkpointRepository.findLatest(runId, CHECKPOINTS_TO_KEEP)) {
            try {
                return jsonCodec.read(loadAndVerify(checkpoint), AgentRuntimeState.class);
            } catch (Exception ex) {
                log.error("Failed to load checkpoint, trying previous: runId={}, checkpointNo={}",
                        runId, checkpoint.checkpointNo(), ex);
            }
        }
        return null;
    }

    public void deleteCheckpoints(Long runId) {
        try {
            checkpointCache.delete(runId);
        } catch (Exception ex) {
            log.warn("Failed to delete checkpoint hot cache: runId={}", runId, ex);
        }
    }

    public boolean shouldCheckpoint(AgentEvent event, AgentRuntimeState state) {
        if (event.eventType().equals("run.started")) {
            return true;
        }
        if (event.eventType().equals("turn.route.completed") || event.eventType().equals("context.resolved")
                || event.eventType().equals("prompt.composed")
                || event.eventType().equals("llm.continuation.saved")) {
            return true;
        }
        if (event.eventType().equals("tool.call.waiting_approval")) {
            return true;
        }
        if (event.eventType().equals("tool.call.started")
                || event.eventType().equals("tool.call.completed")
                || event.eventType().equals("tool.call.failed")) {
            return true;
        }
        if (event.eventType().equals("run.completed")) {
            return true;
        }
        if (event.eventType().equals("run.failed")) {
            return true;
        }
        return false;
    }

    private String serializeState(AgentRuntimeState state) {
        try {
            return jsonCodec.write(state);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Failed to serialize agent runtime state", ex);
        }
    }

    private String loadAndVerify(AgentCheckpoint checkpoint) {
        if (checkpoint.stateSchemaVersion() != STATE_SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported checkpoint state schema: " + checkpoint.stateSchemaVersion());
        }
        byte[] bytes = checkpoint.stateObjectKey() == null || checkpoint.stateObjectKey().isBlank()
                ? checkpoint.stateJson().getBytes(StandardCharsets.UTF_8)
                : objectStorage.readBytes(checkpoint.stateObjectKey());
        if (bytes.length != checkpoint.stateSizeBytes()) {
            throw new IllegalStateException("Checkpoint state size mismatch");
        }
        if (checkpoint.stateSha256() != null && !checkpoint.stateSha256().equals(sha256(bytes))) {
            throw new IllegalStateException("Checkpoint state checksum mismatch");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
