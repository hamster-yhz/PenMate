package com.penmate.backend.application.agent.run;

import com.penmate.backend.domain.agent.run.model.AgentCheckpoint;
import com.penmate.backend.domain.agent.run.repository.AgentCheckpointRepository;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentCheckpointArchiveServiceTest {

    @Test
    void externalizes_and_verifies_inline_state_before_marking_cold() throws Exception {
        AgentCheckpointRepository checkpoints = mock(AgentCheckpointRepository.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        Instant now = java.time.LocalDateTime.of(2026, 7, 17, 3, 15).toInstant(java.time.ZoneOffset.UTC);
        String state = "{\"phase\":\"done\"}";
        AgentCheckpoint checkpoint = hotCheckpoint(state, sha256(state));
        when(checkpoints.findTerminalHotBefore(now.minus(7, java.time.temporal.ChronoUnit.DAYS), 100)).thenReturn(List.of(checkpoint));
        AtomicReference<byte[]> uploaded = new AtomicReference<>();
        when(storage.putBytes(anyString(), any(), eq("application/json"))).thenAnswer(invocation -> {
            byte[] bytes = invocation.getArgument(1);
            uploaded.set(bytes);
            return new ObjectStorageService.PutObjectResult("etag", (long) bytes.length, null);
        });
        when(storage.readBytes(anyString())).thenAnswer(invocation -> uploaded.get());
        when(checkpoints.markCold(anyLong(), anyString(), anyString(), anyString(), any(), any())).thenReturn(1);

        var result = new AgentCheckpointArchiveService(checkpoints, storage).archiveEligible(now);

        assertThat(result.archivedCheckpoints()).isEqualTo(1);
        String key = "agent-runs/70/checkpoints/2-9.json";
        InOrder order = inOrder(storage, checkpoints);
        order.verify(storage).putBytes(eq(key), any(), eq("application/json"));
        order.verify(storage).readBytes(key);
        order.verify(checkpoints).markCold(
                99L, "{\"externalState\":true}", key, sha256(state),
                now, now.plus(90, java.time.temporal.ChronoUnit.DAYS));
    }

    @Test
    void keeps_hot_manifest_when_archive_readback_is_corrupt() throws Exception {
        AgentCheckpointRepository checkpoints = mock(AgentCheckpointRepository.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        Instant now = java.time.LocalDateTime.of(2026, 7, 17, 3, 15).toInstant(java.time.ZoneOffset.UTC);
        String state = "{\"phase\":\"done\"}";
        when(checkpoints.findTerminalHotBefore(now.minus(7, java.time.temporal.ChronoUnit.DAYS), 100))
                .thenReturn(List.of(hotCheckpoint(state, sha256(state))));
        when(storage.putBytes(anyString(), any(), anyString()))
                .thenReturn(new ObjectStorageService.PutObjectResult("etag", (long) state.length(), null));
        when(storage.readBytes(anyString())).thenReturn("corrupt".getBytes(StandardCharsets.UTF_8));

        var result = new AgentCheckpointArchiveService(checkpoints, storage).archiveEligible(now);

        assertThat(result.archivedCheckpoints()).isZero();
        verify(checkpoints, never()).markCold(anyLong(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void purges_object_before_cold_manifest() {
        AgentCheckpointRepository checkpoints = mock(AgentCheckpointRepository.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        Instant now = java.time.LocalDateTime.of(2026, 10, 15, 3, 15).toInstant(java.time.ZoneOffset.UTC);
        AgentCheckpoint cold = new AgentCheckpoint(
                99L, 70L, 2L, 9L, "{\"externalState\":true}", 2, 1, "a".repeat(64),
                "agent-runs/70/checkpoints/2-9.json", "COLD", now.minus(90, java.time.temporal.ChronoUnit.DAYS), now, now.minus(97, java.time.temporal.ChronoUnit.DAYS));
        when(checkpoints.findExpiredCold(now, 100)).thenReturn(List.of(cold));
        when(checkpoints.deleteCold(99L)).thenReturn(1);

        var result = new AgentCheckpointArchiveService(checkpoints, storage).purgeExpired(now);

        assertThat(result.purgedCheckpoints()).isEqualTo(1);
        InOrder order = inOrder(storage, checkpoints);
        order.verify(storage).delete(cold.stateObjectKey());
        order.verify(checkpoints).deleteCold(99L);
    }

    private AgentCheckpoint hotCheckpoint(String state, String hash) {
        return new AgentCheckpoint(
                99L, 70L, 2L, 9L, state, state.getBytes(StandardCharsets.UTF_8).length,
                1, hash, null, "HOT", null, null, java.time.LocalDateTime.of(2026, 7, 1, 0, 0).toInstant(java.time.ZoneOffset.UTC));
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
