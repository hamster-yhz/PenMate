package com.penmate.backend.application.storybible;

import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleChangeItem;
import com.penmate.backend.domain.storybible.model.StoryBibleChangeset;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Service
@Slf4j
public class StoryBibleHistoryArchiveService {

    static final int HOT_RETENTION_DAYS = 180;
    static final int HOT_RETENTION_COUNT = 5_000;
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final StoryBibleRepository repository;
    private final ObjectStorageService objectStorageService;
    private final JsonCodec jsonCodec;
    private final StoryBibleHistoryDeletionService deletionService;

    public StoryBibleHistoryArchiveService(StoryBibleRepository repository,
                                           ObjectStorageService objectStorageService,
                                           JsonCodec jsonCodec,
                                           StoryBibleHistoryDeletionService deletionService) {
        this.repository = repository;
        this.objectStorageService = objectStorageService;
        this.jsonCodec = jsonCodec;
        this.deletionService = deletionService;
    }

    public void archiveEligibleHistory() {
        archiveEligibleHistory(Instant.now());
    }

    public ArchiveSummary archiveEligibleHistory(Instant now) {
        Instant cutoff = now.minus(HOT_RETENTION_DAYS, ChronoUnit.DAYS);
        int projectCount = 0;
        int changesetCount = 0;
        int archiveCount = 0;
        for (StoryBible storyBible : repository.findStoryBiblesWithChangesetsBefore(cutoff)) {
            try {
                ArchiveSummary result = archiveStoryBible(storyBible, cutoff);
                if (result.changesetCount() > 0) projectCount++;
                changesetCount += result.changesetCount();
                archiveCount += result.archiveCount();
            } catch (RuntimeException ex) {
                log.error("story_bible.history.archive.failed: projectId={}, storyBibleId={}",
                        storyBible.getProjectId(), storyBible.getStoryBibleId(), ex);
            }
        }
        return new ArchiveSummary(projectCount, archiveCount, changesetCount);
    }

    ArchiveSummary archiveStoryBible(StoryBible storyBible, Instant cutoff) {
        List<StoryBibleChangeset> eligible = repository.findChangesetsBefore(
                storyBible.getStoryBibleId(), cutoff, HOT_RETENTION_COUNT);
        Map<YearMonth, List<StoryBibleChangeset>> byMonth = new TreeMap<>();
        for (StoryBibleChangeset changeset : eligible) {
            if (changeset.getCreatedAt() == null) {
                throw new IllegalStateException("Story Bible changeset is missing createdAt");
            }
            byMonth.computeIfAbsent(
                            YearMonth.from(changeset.getCreatedAt().atZone(ZoneOffset.UTC)),
                            ignored -> new ArrayList<>())
                    .add(changeset);
        }

        int archived = 0;
        for (Map.Entry<YearMonth, List<StoryBibleChangeset>> entry : byMonth.entrySet()) {
            archiveMonth(storyBible, entry.getKey(), entry.getValue());
            archived += entry.getValue().size();
        }
        return new ArchiveSummary(archived == 0 ? 0 : 1, byMonth.size(), archived);
    }

    private void archiveMonth(StoryBible storyBible, YearMonth month, List<StoryBibleChangeset> changesets) {
        changesets.sort(Comparator.comparing(StoryBibleChangeset::getCreatedAt)
                .thenComparing(StoryBibleChangeset::getChangesetId));
        List<Long> changesetIds = changesets.stream().map(StoryBibleChangeset::getChangesetId).toList();
        List<StoryBibleChangeItem> items = repository.findChangeItemsByChangesetIds(changesetIds);
        Map<Long, List<StoryBibleChangeItem>> itemsByChangeset = new HashMap<>();
        for (StoryBibleChangeItem item : items) {
            itemsByChangeset.computeIfAbsent(item.getChangesetId(), ignored -> new ArrayList<>()).add(item);
        }

        String objectKey = objectKey(storyBible.getProjectId(), month);
        TreeMap<Long, String> mergedLines = objectStorageService.exists(objectKey)
                ? decodeArchive(objectStorageService.readBytes(objectKey))
                : new TreeMap<>();
        for (StoryBibleChangeset changeset : changesets) {
            mergedLines.put(changeset.getChangesetId(), archiveLine(
                    storyBible, changeset, itemsByChangeset.getOrDefault(changeset.getChangesetId(), List.of())));
        }

        byte[] payload = gzip(String.join("\n", mergedLines.values()) + "\n");
        ObjectStorageService.PutObjectResult uploaded = objectStorageService.putBytes(
                objectKey, payload, "application/gzip");
        if (uploaded == null || uploaded.size() == null || uploaded.size() != payload.length) {
            throw new IllegalStateException("Story Bible history archive upload size mismatch");
        }
        byte[] verified = objectStorageService.readBytes(objectKey);
        if (verified.length != payload.length || !sha256(verified).equals(sha256(payload))) {
            throw new IllegalStateException("Story Bible history archive checksum mismatch");
        }

        deletionService.deleteVerifiedArchive(storyBible.getStoryBibleId(), changesetIds, items.size());
        log.info("story_bible.history.archived: projectId={}, storyBibleId={}, month={}, changesets={}, bytes={}, sha256={}",
                storyBible.getProjectId(), storyBible.getStoryBibleId(), month, changesets.size(),
                payload.length, sha256(payload));
    }

    private TreeMap<Long, String> decodeArchive(byte[] bytes) {
        try {
            String jsonl = ungzip(bytes);
            TreeMap<Long, String> lines = new TreeMap<>();
            for (String line : jsonl.split("\\R")) {
                if (line.isBlank()) continue;
                Map<String, Object> root = jsonCodec.readObject(line);
                Object changesetValue = root.get("changeset");
                if (!(changesetValue instanceof Map<?, ?> changeset)) {
                    throw new IllegalStateException("Story Bible history archive line is missing changesetId");
                }
                lines.put(requiredLong(changeset.get("changesetId")), line);
            }
            return lines;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read existing Story Bible history archive", ex);
        }
    }

    private String archiveLine(StoryBible storyBible, StoryBibleChangeset changeset,
                               List<StoryBibleChangeItem> items) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("schemaVersion", 1);
        record.put("projectId", String.valueOf(storyBible.getProjectId()));
        record.put("storyBibleId", String.valueOf(storyBible.getStoryBibleId()));
        record.put("changeset", changeset);
        record.put("items", items);
        try {
            return jsonCodec.write(record);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to serialize Story Bible history archive", ex);
        }
    }

    private long requiredLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String string && string.matches("\\d+")) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException ignored) {
                // Fall through to the archive corruption error.
            }
        }
        throw new IllegalStateException("Story Bible history archive line is missing changesetId");
    }

    private byte[] gzip(String value) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to gzip Story Bible history archive", ex);
        }
    }

    private String ungzip(byte[] value) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(value))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to gunzip Story Bible history archive", ex);
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    static String objectKey(Long projectId, YearMonth month) {
        return "story-bible-history/" + projectId + "/" + month.format(MONTH_FORMAT) + ".jsonl.gz";
    }

    public record ArchiveSummary(int projectCount, int archiveCount, int changesetCount) {
    }
}
