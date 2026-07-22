package com.penmate.backend.application.storybible;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleActorType;
import com.penmate.backend.domain.storybible.model.StoryBibleChangeItem;
import com.penmate.backend.domain.storybible.model.StoryBibleChangeOperation;
import com.penmate.backend.domain.storybible.model.StoryBibleChangeset;
import com.penmate.backend.domain.storybible.repository.StoryBibleRepository;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoryBibleHistoryArchiveServiceTest {

    @Mock
    private StoryBibleRepository repository;
    @Mock
    private ObjectStorageService storage;
    @Mock
    private StoryBibleHistoryDeletionService deletionService;

    private StoryBibleHistoryArchiveService service;
    private AtomicReference<byte[]> uploaded;

    @BeforeEach
    void setUp() {
        service = new StoryBibleHistoryArchiveService(
                repository, storage,
                new JacksonJsonCodec(new ObjectMapper().findAndRegisterModules()), deletionService);
        uploaded = new AtomicReference<>();
    }

    @Test
    void should_archive_monthly_gzip_verify_it_and_only_then_delete_hot_rows() throws Exception {
        StoryBible root = root();
        StoryBibleChangeset changeset = changeset(501L, java.time.LocalDateTime.of(2025, 1, 12, 10, 30).toInstant(java.time.ZoneOffset.UTC));
        StoryBibleChangeItem item = item(601L, 501L);
        when(repository.findChangesetsBefore(11L, java.time.LocalDateTime.of(2025, 2, 1, 0, 0).toInstant(java.time.ZoneOffset.UTC), 5_000))
                .thenReturn(List.of(changeset));
        when(repository.findChangeItemsByChangesetIds(List.of(501L))).thenReturn(List.of(item));
        when(storage.exists("story-bible-history/22/2025-01.jsonl.gz")).thenReturn(false);
        wireSuccessfulStorage("story-bible-history/22/2025-01.jsonl.gz");

        var summary = service.archiveStoryBible(root, java.time.LocalDateTime.of(2025, 2, 1, 0, 0).toInstant(java.time.ZoneOffset.UTC));

        assertThat(summary).isEqualTo(new StoryBibleHistoryArchiveService.ArchiveSummary(1, 1, 1));
        String jsonl = ungzip(uploaded.get());
        assertThat(jsonl).contains("\"projectId\":\"22\"")
                .contains("\"changesetId\":501")
                .contains("\"changeItemId\":601");
        verify(storage).putBytes("story-bible-history/22/2025-01.jsonl.gz",
                uploaded.get(), "application/gzip");
        verify(deletionService).deleteVerifiedArchive(11L, List.of(501L), 1);
    }

    @Test
    void should_merge_with_an_existing_month_archive_instead_of_overwriting_it() throws Exception {
        StoryBible root = root();
        StoryBibleChangeset changeset = changeset(502L, java.time.LocalDateTime.of(2025, 1, 20, 9, 0).toInstant(java.time.ZoneOffset.UTC));
        byte[] existing = gzip("""
                {"schemaVersion":1,"projectId":"22","storyBibleId":"11","changeset":{"changesetId":501},"items":[]}
                """);
        String key = "story-bible-history/22/2025-01.jsonl.gz";
        when(repository.findChangesetsBefore(eq(11L), any(), eq(5_000))).thenReturn(List.of(changeset));
        when(repository.findChangeItemsByChangesetIds(List.of(502L))).thenReturn(List.of());
        when(storage.exists(key)).thenReturn(true);
        when(storage.readBytes(key)).thenReturn(existing).thenAnswer(ignored -> uploaded.get());
        when(storage.putBytes(eq(key), any(), eq("application/gzip"))).thenAnswer(invocation -> {
            byte[] bytes = invocation.getArgument(1);
            uploaded.set(bytes);
            return new ObjectStorageService.PutObjectResult("etag", (long) bytes.length, null);
        });

        service.archiveStoryBible(root, java.time.LocalDateTime.of(2025, 2, 1, 0, 0).toInstant(java.time.ZoneOffset.UTC));

        String jsonl = ungzip(uploaded.get());
        assertThat(jsonl).contains("\"changesetId\":501").contains("\"changesetId\":502");
        verify(deletionService).deleteVerifiedArchive(11L, List.of(502L), 0);
    }

    @Test
    void should_keep_database_history_when_uploaded_bytes_fail_verification() {
        StoryBible root = root();
        StoryBibleChangeset changeset = changeset(501L, java.time.LocalDateTime.of(2025, 1, 12, 10, 30).toInstant(java.time.ZoneOffset.UTC));
        String key = "story-bible-history/22/2025-01.jsonl.gz";
        when(repository.findChangesetsBefore(eq(11L), any(), eq(5_000))).thenReturn(List.of(changeset));
        when(repository.findChangeItemsByChangesetIds(List.of(501L))).thenReturn(List.of());
        when(storage.exists(key)).thenReturn(false);
        when(storage.putBytes(eq(key), any(), eq("application/gzip"))).thenAnswer(invocation -> {
            byte[] bytes = invocation.getArgument(1);
            return new ObjectStorageService.PutObjectResult("etag", (long) bytes.length, null);
        });
        when(storage.readBytes(key)).thenReturn(new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.archiveStoryBible(root, java.time.LocalDateTime.of(2025, 2, 1, 0, 0).toInstant(java.time.ZoneOffset.UTC)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checksum mismatch");
        verify(deletionService, never()).deleteVerifiedArchive(any(), any(), any(Integer.class));
    }

    @Test
    void should_select_only_rows_outside_both_hot_retention_windows() {
        Instant now = java.time.LocalDateTime.of(2026, 7, 16, 4, 0).toInstant(java.time.ZoneOffset.UTC);
        StoryBible root = root();
        when(repository.findStoryBiblesWithChangesetsBefore(now.minus(180, java.time.temporal.ChronoUnit.DAYS))).thenReturn(List.of(root));
        when(repository.findChangesetsBefore(11L, now.minus(180, java.time.temporal.ChronoUnit.DAYS), 5_000)).thenReturn(List.of());

        assertThat(service.archiveEligibleHistory(now).changesetCount()).isZero();
        verify(repository).findChangesetsBefore(11L, now.minus(180, java.time.temporal.ChronoUnit.DAYS), 5_000);
    }

    private void wireSuccessfulStorage(String key) {
        when(storage.putBytes(eq(key), any(), eq("application/gzip"))).thenAnswer(invocation -> {
            byte[] bytes = invocation.getArgument(1);
            uploaded.set(bytes);
            return new ObjectStorageService.PutObjectResult("etag", (long) bytes.length, null);
        });
        when(storage.readBytes(key)).thenAnswer(ignored -> uploaded.get());
    }

    private StoryBible root() {
        StoryBible root = new StoryBible();
        root.setStoryBibleId(11L);
        root.setProjectId(22L);
        return root;
    }

    private StoryBibleChangeset changeset(Long id, Instant createdAt) {
        StoryBibleChangeset changeset = new StoryBibleChangeset();
        changeset.setChangesetId(id);
        changeset.setStoryBibleId(11L);
        changeset.setContentRevision(id);
        changeset.setActorType(StoryBibleActorType.USER);
        changeset.setActorId(7L);
        changeset.setChangeSummary("Changed");
        changeset.setCreatedAt(createdAt);
        return changeset;
    }

    private StoryBibleChangeItem item(Long id, Long changesetId) {
        StoryBibleChangeItem item = new StoryBibleChangeItem();
        item.setChangeItemId(id);
        item.setChangesetId(changesetId);
        item.setEntityType("NODE");
        item.setEntityId(91L);
        item.setOperation(StoryBibleChangeOperation.UPDATE);
        item.setFieldPath("/summary");
        item.setBeforeJson("\"old\"");
        item.setAfterJson("\"new\"");
        return item;
    }

    private byte[] gzip(String value) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            return output.toByteArray();
        }
    }

    private String ungzip(byte[] value) throws Exception {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(value))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
