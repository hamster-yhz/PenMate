package com.penmate.backend.application.agent.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentEventArchive;
import com.penmate.backend.domain.agent.run.repository.AgentEventArchiveRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunEventRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AgentEventArchiveServiceTest {

    @Test
    void verifies_archive_before_deleting_hot_events() {
        AgentRunEventRepository events = mock(AgentRunEventRepository.class);
        AgentEventArchiveRepository archives = mock(AgentEventArchiveRepository.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        Instant now = java.time.LocalDateTime.of(2026, 7, 17, 3, 0).toInstant(java.time.ZoneOffset.UTC);
        when(events.findTerminalRunIdsWithEventsBefore(now.minus(7, java.time.temporal.ChronoUnit.DAYS), 100)).thenReturn(List.of(70L));
        when(events.listAfter(70L, 0L)).thenReturn(List.of(event(1L), event(2L)));
        when(archives.upsertUploaded(any())).thenReturn(1);
        when(archives.markVerified(99L, now)).thenReturn(1);
        AtomicReference<byte[]> uploaded = new AtomicReference<>();
        when(storage.putBytes(any(), any(), eq("application/gzip"))).thenAnswer(invocation -> {
            byte[] bytes = invocation.getArgument(1);
            uploaded.set(bytes);
            return new ObjectStorageService.PutObjectResult("etag", (long) bytes.length, null);
        });
        when(storage.readBytes(any())).thenAnswer(invocation -> uploaded.get());
        BusinessIdGenerator ids = () -> 99L;

        var result = new AgentEventArchiveService(
                events, archives, storage, ids,
                new JacksonJsonCodec(new ObjectMapper().findAndRegisterModules()))
                .archiveEligible(now);

        assertThat(result.archivedRuns()).isEqualTo(1);
        assertThat(result.archivedEvents()).isEqualTo(2);
        InOrder order = inOrder(archives, events);
        order.verify(archives).upsertUploaded(any());
        order.verify(archives).markVerified(99L, now);
        order.verify(events).deleteThrough(70L, 2L);
    }

    @Test
    void keeps_hot_events_when_readback_verification_fails() {
        AgentRunEventRepository events = mock(AgentRunEventRepository.class);
        AgentEventArchiveRepository archives = mock(AgentEventArchiveRepository.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        Instant now = java.time.LocalDateTime.of(2026, 7, 17, 3, 0).toInstant(java.time.ZoneOffset.UTC);
        when(events.findTerminalRunIdsWithEventsBefore(now.minus(7, java.time.temporal.ChronoUnit.DAYS), 100)).thenReturn(List.of(70L));
        when(events.listAfter(70L, 0L)).thenReturn(List.of(event(1L)));
        when(storage.putBytes(any(), any(), any())).thenAnswer(invocation -> {
            byte[] bytes = invocation.getArgument(1);
            return new ObjectStorageService.PutObjectResult("etag", (long) bytes.length, null);
        });
        when(storage.readBytes(any())).thenReturn(new byte[]{1, 2, 3});

        var result = new AgentEventArchiveService(
                events, archives, storage, () -> 99L,
                new JacksonJsonCodec(new ObjectMapper().findAndRegisterModules()))
                .archiveEligible(now);

        assertThat(result.archivedRuns()).isZero();
        verify(archives, never()).upsertUploaded(any());
        verify(events, never()).deleteThrough(any(), any());
    }

    @Test
    void purges_verified_archive_after_cold_retention() {
        AgentRunEventRepository events = mock(AgentRunEventRepository.class);
        AgentEventArchiveRepository archives = mock(AgentEventArchiveRepository.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        Instant now = java.time.LocalDateTime.of(2026, 10, 15, 3, 0).toInstant(java.time.ZoneOffset.UTC);
        AgentEventArchive archive = new AgentEventArchive(
                99L, 70L, 1L, 2L, 2, "agent-runs/70/events/1-2.jsonl.gz",
                100L, "hash", "VERIFIED", now.minus(90, java.time.temporal.ChronoUnit.DAYS), now, now.minus(90, java.time.temporal.ChronoUnit.DAYS));
        when(archives.findExpiredVerified(now, 100)).thenReturn(List.of(archive));
        when(archives.delete(99L)).thenReturn(1);

        var result = new AgentEventArchiveService(
                events, archives, storage, () -> 100L,
                new JacksonJsonCodec(new ObjectMapper())).purgeExpired(now);

        assertThat(result.purgedArchives()).isEqualTo(1);
        InOrder order = inOrder(storage, archives);
        order.verify(storage).delete(archive.objectKey());
        order.verify(archives).delete(99L);
    }

    private AgentEvent event(long sequence) {
        return new AgentEvent(100L + sequence, 70L, 10L, 20L, 30L, sequence,
                1, sequence == 2 ? "run.completed" : "run.started", "{}",
                java.time.LocalDateTime.of(2026, 7, 1, 0, 0).toInstant(java.time.ZoneOffset.UTC).plusSeconds(sequence));
    }
}
