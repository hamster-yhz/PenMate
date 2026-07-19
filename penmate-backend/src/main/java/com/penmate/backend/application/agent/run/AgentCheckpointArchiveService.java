package com.penmate.backend.application.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentCheckpoint;
import com.penmate.backend.domain.agent.run.repository.AgentCheckpointRepository;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

@Service
@Slf4j
public class AgentCheckpointArchiveService {

    static final int HOT_RETENTION_DAYS = 7;
    static final int COLD_RETENTION_DAYS = 90;
    private static final int BATCH_SIZE = 100;
    private static final String EXTERNAL_STATE_MARKER = "{\"externalState\":true}";

    private final AgentCheckpointRepository checkpoints;
    private final ObjectStorageService storage;

    public AgentCheckpointArchiveService(AgentCheckpointRepository checkpoints,
                                         ObjectStorageService storage) {
        this.checkpoints = checkpoints;
        this.storage = storage;
    }

    public ArchiveSummary archiveEligible(Instant now) {
        int archived = 0;
        for (AgentCheckpoint checkpoint : checkpoints.findTerminalHotBefore(
                now.minus(HOT_RETENTION_DAYS, ChronoUnit.DAYS), BATCH_SIZE)) {
            try {
                archive(checkpoint, now);
                archived++;
            } catch (RuntimeException ex) {
                log.error("agent.checkpoint.archive.failed: runId={}, checkpointId={}",
                        checkpoint.runId(), checkpoint.checkpointId(), ex);
            }
        }
        return new ArchiveSummary(archived, 0);
    }

    private void archive(AgentCheckpoint checkpoint, Instant now) {
        byte[] state = loadHotState(checkpoint);
        String hash = sha256(state);
        verifyState(checkpoint, state, hash);

        String objectKey = checkpoint.stateObjectKey();
        if (objectKey == null || objectKey.isBlank()) {
            objectKey = checkpointObjectKey(checkpoint);
            ObjectStorageService.PutObjectResult stored = storage.putBytes(
                    objectKey, state, "application/json");
            if (stored == null || stored.size() == null || stored.size() != state.length) {
                throw new IllegalStateException("Agent checkpoint archive upload size mismatch");
            }
        }

        byte[] readBack = storage.readBytes(objectKey);
        if (readBack.length != state.length || !hash.equals(sha256(readBack))) {
            throw new IllegalStateException("Agent checkpoint archive integrity check failed");
        }

        int affected = checkpoints.markCold(
                checkpoint.checkpointId(), EXTERNAL_STATE_MARKER, objectKey, hash,
                now, now.plus(COLD_RETENTION_DAYS, ChronoUnit.DAYS));
        if (affected != 1) {
            throw new IllegalStateException("Failed to mark Agent checkpoint cold");
        }
        log.info("agent.checkpoint.archived: runId={}, checkpointId={}, objectKey={}, sha256={}",
                checkpoint.runId(), checkpoint.checkpointId(), objectKey, hash);
    }

    public ArchiveSummary purgeExpired(Instant now) {
        int purged = 0;
        for (AgentCheckpoint checkpoint : checkpoints.findExpiredCold(now, BATCH_SIZE)) {
            try {
                storage.delete(checkpoint.stateObjectKey());
                if (checkpoints.deleteCold(checkpoint.checkpointId()) != 1) {
                    throw new IllegalStateException("Failed to delete cold Agent checkpoint manifest");
                }
                purged++;
            } catch (RuntimeException ex) {
                log.error("agent.checkpoint.archive.purge.failed: runId={}, checkpointId={}",
                        checkpoint.runId(), checkpoint.checkpointId(), ex);
            }
        }
        return new ArchiveSummary(0, purged);
    }

    private byte[] loadHotState(AgentCheckpoint checkpoint) {
        if (checkpoint.stateObjectKey() != null && !checkpoint.stateObjectKey().isBlank()) {
            return storage.readBytes(checkpoint.stateObjectKey());
        }
        return checkpoint.stateJson().getBytes(StandardCharsets.UTF_8);
    }

    private void verifyState(AgentCheckpoint checkpoint, byte[] state, String actualHash) {
        if (state.length != checkpoint.stateSizeBytes()) {
            throw new IllegalStateException("Agent checkpoint archive state size mismatch");
        }
        if (checkpoint.stateSha256() != null && !checkpoint.stateSha256().equals(actualHash)) {
            throw new IllegalStateException("Agent checkpoint archive checksum mismatch");
        }
    }

    private String checkpointObjectKey(AgentCheckpoint checkpoint) {
        return "agent-runs/" + checkpoint.runId() + "/checkpoints/"
                + checkpoint.checkpointNo() + "-" + checkpoint.lastEventSeq() + ".json";
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public record ArchiveSummary(int archivedCheckpoints, int purgedCheckpoints) {
    }
}
