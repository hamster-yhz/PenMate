package com.penmate.backend.application.agent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.agent.context.model.AgentContextEpoch;
import com.penmate.backend.domain.agent.context.repository.AgentContextEpochRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentContextEpochServiceTest {

    private final AgentContextEpochRepository repository = mock(AgentContextEpochRepository.class);
    private final BusinessIdGenerator idGenerator = mock(BusinessIdGenerator.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final ContextEpochSnapshotCache cache = mock(ContextEpochSnapshotCache.class);
    private final AgentContextEpochService service = new AgentContextEpochService(repository, idGenerator, storage, new ObjectMapper(), cache);

    @Test
    void should_create_immutable_epoch_and_bind_session_and_run() {
        byte[] snapshot = "{\"catalog\":[]}".getBytes(StandardCharsets.UTF_8);
        when(idGenerator.nextId()).thenReturn(900L);
        when(repository.lockSession(20L)).thenReturn(20L);
        when(repository.nextEpochNo(20L)).thenReturn(1);
        when(storage.putText(anyString(), anyString(), anyString()))
                .thenReturn(new ObjectStorageService.PutObjectResult("etag", (long) snapshot.length, null));
        when(storage.readText(anyString())).thenReturn("{\"catalog\":[]}");
        when(repository.insert(any())).thenReturn(1);
        when(repository.bindSession(20L, 900L)).thenReturn(1);
        when(repository.bindRun(30L, 900L)).thenReturn(1);

        var binding = service.bind(request("{\"catalog\":[]}"));

        assertThat(binding.reused()).isFalse();
        assertThat(binding.epoch().epochNo()).isEqualTo(1);
        assertThat(binding.epoch().snapshotHash()).hasSize(64);
        verify(repository).supersedeCurrent(20L, 900L);
        verify(repository).bindSession(20L, 900L);
        verify(repository).bindRun(30L, 900L);
    }

    @Test
    void should_reuse_current_epoch_on_exact_fingerprint_even_if_dynamic_snapshot_differs() {
        AgentContextEpoch current = new AgentContextEpoch(
                900L, 20L, 1, "ignored", 4L, 3L, 40L, 2L, 0L,
                "RETRIEVAL_THEN_LLM", null, "prompt", "skills", "tools",
                "key", "hash", 2L, null, null);
        when(repository.lockSession(20L)).thenReturn(20L);
        when(repository.findCurrentByFingerprint(any(), anyString())).thenReturn(current);
        when(repository.bindRun(30L, 900L)).thenReturn(1);

        var binding = service.bind(request("{\"workingSet\":[99]}"));

        assertThat(binding.reused()).isTrue();
        assertThat(binding.epoch()).isSameAs(current);
        verify(storage, never()).putText(anyString(), anyString(), anyString());
        verify(repository, never()).insert(any());
    }

    @Test
    void should_not_publish_epoch_metadata_when_object_readback_is_corrupt() {
        byte[] snapshot = "{\"catalog\":[]}".getBytes(StandardCharsets.UTF_8);
        when(idGenerator.nextId()).thenReturn(900L);
        when(repository.lockSession(20L)).thenReturn(20L);
        when(repository.nextEpochNo(20L)).thenReturn(1);
        when(storage.putText(anyString(), anyString(), anyString()))
                .thenReturn(new ObjectStorageService.PutObjectResult("etag", (long) snapshot.length, null));
        when(storage.readText(anyString())).thenReturn("{\"corrupt\":[]}");

        assertThatThrownBy(() -> service.bind(request("{\"catalog\":[]}")))
                .hasMessageContaining("verification failed");

        verify(repository, never()).insert(any());
        verify(repository, never()).bindSession(any(), any());
        verify(repository, never()).bindRun(any(), any());
    }

    private AgentContextEpochService.BindRequest request(String snapshotJson) {
        return new AgentContextEpochService.BindRequest(
                20L, 30L, 4L, 3L, 40L, 2L, 0L, "RETRIEVAL_THEN_LLM",
                null, "prompt", "skills", "tools", snapshotJson
        );
    }
}
