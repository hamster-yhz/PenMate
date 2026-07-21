package com.penmate.backend.application.agent.run;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentEventArchive;
import com.penmate.backend.domain.agent.run.repository.AgentEventArchiveRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunEventRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Service
@Slf4j
public class AgentEventArchiveService {

    static final int HOT_RETENTION_DAYS = 7;
    static final int COLD_RETENTION_DAYS = 90;
    private static final int BATCH_SIZE = 100;

    private final AgentRunEventRepository events;
    private final AgentEventArchiveRepository archives;
    private final ObjectStorageService storage;
    private final BusinessIdGenerator ids;
    private final ObjectMapper objectMapper;

    public AgentEventArchiveService(AgentRunEventRepository events,
                                    AgentEventArchiveRepository archives,
                                    ObjectStorageService storage,
                                    BusinessIdGenerator ids,
                                    ObjectMapper objectMapper) {
        this.events = events;
        this.archives = archives;
        this.storage = storage;
        this.ids = ids;
        this.objectMapper = objectMapper;
    }

    public ArchiveSummary archiveEligible(Instant now) {
        int archivedRuns = 0;
        int archivedEvents = 0;
        for (Long runId : events.findTerminalRunIdsWithEventsBefore(
                now.minus(HOT_RETENTION_DAYS, ChronoUnit.DAYS), BATCH_SIZE)) {
            try {
                int count = archiveRun(runId, now);
                if (count > 0) archivedRuns++;
                archivedEvents += count;
            } catch (RuntimeException ex) {
                log.error("agent.events.archive.failed: runId={}", runId, ex);
            }
        }
        return new ArchiveSummary(archivedRuns, archivedEvents, 0);
    }

    int archiveRun(Long runId, Instant now) {
        List<AgentEvent> runEvents = events.listAfter(runId, 0L);
        if (runEvents.isEmpty()) return 0;
        AgentEventArchive existing = archives.findByRunId(runId);
        long lastSequence = runEvents.get(runEvents.size() - 1).sequence();
        if (existing != null && existing.verified() && existing.lastSequence() >= lastSequence) {
            events.deleteThrough(runId, existing.lastSequence());
            return 0;
        }

        byte[] payload = gzip(jsonl(runEvents));
        String hash = sha256(payload);
        long firstSequence = runEvents.get(0).sequence();
        String key = "agent-runs/" + runId + "/events/" + firstSequence + "-" + lastSequence + ".jsonl.gz";
        ObjectStorageService.PutObjectResult stored = storage.putBytes(key, payload, "application/gzip");
        if (stored == null || stored.size() == null || stored.size() != payload.length) {
            throw new IllegalStateException("Agent Event archive upload size mismatch");
        }
        byte[] verifiedBytes = storage.readBytes(key);
        verifyArchive(verifiedBytes, hash, firstSequence, lastSequence, runEvents.size());

        Long archiveId = existing == null ? ids.nextId() : existing.archiveId();
        AgentEventArchive uploaded = new AgentEventArchive(
                archiveId, runId, firstSequence, lastSequence, runEvents.size(), key,
                (long) payload.length, hash, "UPLOADED", null,
                now.plus(COLD_RETENTION_DAYS, ChronoUnit.DAYS), null);
        requirePositive(archives.upsertUploaded(uploaded), "Failed to persist Agent Event archive manifest");
        requireOne(archives.markVerified(archiveId, now), "Failed to verify Agent Event archive manifest");
        events.deleteThrough(runId, lastSequence);
        log.info("agent.events.archived: runId={}, firstSequence={}, lastSequence={}, count={}, sha256={}",
                runId, firstSequence, lastSequence, runEvents.size(), hash);
        return runEvents.size();
    }

    public ArchiveSummary purgeExpired(Instant now) {
        int purged = 0;
        for (AgentEventArchive archive : archives.findExpiredVerified(now, BATCH_SIZE)) {
            try {
                storage.delete(archive.objectKey());
                requireOne(archives.delete(archive.archiveId()), "Failed to delete Agent Event archive manifest");
                purged++;
            } catch (RuntimeException ex) {
                log.error("agent.events.archive.purge.failed: runId={}, archiveId={}",
                        archive.runId(), archive.archiveId(), ex);
            }
        }
        return new ArchiveSummary(0, 0, purged);
    }

    public List<AgentEvent> readArchived(Long runId) {
        AgentEventArchive archive = archives.findByRunId(runId);
        if (archive == null || !archive.verified()) return List.of();
        byte[] bytes = storage.readBytes(archive.objectKey());
        if (!archive.sha256().equals(sha256(bytes))) {
            throw new IllegalStateException("Agent Event archive checksum mismatch");
        }
        try {
            List<AgentEvent> result = new ArrayList<>();
            for (String line : ungzip(bytes).split("\\R")) {
                if (!line.isBlank()) result.add(objectMapper.readValue(line, AgentEvent.class));
            }
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read Agent Event archive", ex);
        }
    }

    private String jsonl(List<AgentEvent> runEvents) {
        try {
            StringBuilder output = new StringBuilder();
            for (AgentEvent event : runEvents) {
                output.append(objectMapper.writeValueAsString(event)).append('\n');
            }
            return output.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize Agent Event archive", ex);
        }
    }

    private void verifyArchive(byte[] bytes, String expectedHash, long expectedFirst,
                               long expectedLast, int expectedCount) {
        if (!expectedHash.equals(sha256(bytes))) {
            throw new IllegalStateException("Agent Event archive checksum mismatch");
        }
        try {
            String content = ungzip(bytes);
            long first = -1;
            long last = -1;
            int count = 0;
            for (String line : content.split("\\R")) {
                if (line.isBlank()) continue;
                JsonNode root = objectMapper.readTree(line);
                long sequence = root.path("sequence").asLong(-1);
                if (sequence < 0 || (last >= 0 && sequence != last + 1)) {
                    throw new IllegalStateException("Agent Event archive sequence gap");
                }
                if (first < 0) first = sequence;
                last = sequence;
                count++;
            }
            if (first != expectedFirst || last != expectedLast || count != expectedCount) {
                throw new IllegalStateException("Agent Event archive manifest mismatch");
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to verify Agent Event archive", ex);
        }
    }

    private byte[] gzip(String value) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to gzip Agent Event archive", ex);
        }
    }

    private String ungzip(byte[] value) throws Exception {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(value))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private void requireOne(int affected, String message) {
        if (affected != 1) throw new IllegalStateException(message);
    }

    private void requirePositive(int affected, String message) {
        if (affected < 1) throw new IllegalStateException(message);
    }

    public record ArchiveSummary(int archivedRuns, int archivedEvents, int purgedArchives) {
    }
}
