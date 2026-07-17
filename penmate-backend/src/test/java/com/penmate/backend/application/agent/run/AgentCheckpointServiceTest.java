package com.penmate.backend.application.agent.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.agent.run.model.AgentCheckpoint;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRuntimeState;
import com.penmate.backend.domain.agent.run.repository.AgentCheckpointRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.atomic.AtomicLong;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentCheckpointServiceTest {

    @Mock
    private AgentCheckpointRepository checkpointRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ObjectStorageService objectStorage;

    @Test
    void saves_checkpoint_for_policy_event_with_next_checkpoint_number() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(checkpointRepository.findLatest(70001L))
                .thenReturn(new AgentCheckpoint(80000L, 70001L, 1L, 1L, "{}", 2, null));
        AgentCheckpointService service = new AgentCheckpointService(
                checkpointRepository,
                redisTemplate,
                incrementingIds(81000L),
                new ObjectMapper(),
                objectStorage
        );
        AgentRuntimeState state = AgentRuntimeState.empty(70001L);

        service.checkpointIfNeeded(
                AgentEvent.replay(1L, 70001L, 2L, "tool.call.waiting_approval", "{\"approvalId\":88001}"),
                state
        );

        ArgumentCaptor<AgentCheckpoint> captor = ArgumentCaptor.forClass(AgentCheckpoint.class);
        verify(checkpointRepository).save(captor.capture());
        assertThat(captor.getValue().checkpointId()).isEqualTo(81001L);
        assertThat(captor.getValue().checkpointNo()).isEqualTo(2L);
        assertThat(captor.getValue().lastEventSeq()).isEqualTo(2L);
    }

    @Test
    void skips_checkpoint_when_policy_does_not_match() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        AgentCheckpointService service = new AgentCheckpointService(
                checkpointRepository,
                redisTemplate,
                incrementingIds(81000L),
                new ObjectMapper(),
                objectStorage
        );

        service.checkpointIfNeeded(
                AgentEvent.replay(2L, 70001L, 2L, "message.delta", "{\"text\":\"a\"}"),
                AgentRuntimeState.empty(70001L)
        );

        verify(checkpointRepository, never()).save(any());
    }

    @Test
    void does_not_checkpoint_by_arbitrary_event_count() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        AgentCheckpointService service = new AgentCheckpointService(
                checkpointRepository,
                redisTemplate,
                incrementingIds(81000L),
                new ObjectMapper(),
                objectStorage
        );

        assertThat(service.shouldCheckpoint(
                AgentEvent.replay(15L, 70001L, 15L, "message.delta", "{\"text\":\"a\"}"),
                AgentRuntimeState.empty(70001L)
        )).isFalse();
    }

    @Test
    void stores_large_state_in_object_storage_and_keeps_recoverable_metadata() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        AtomicReference<String> uploaded = new AtomicReference<>();
        when(objectStorage.putText(any(), any(), any())).thenAnswer(invocation -> {
            String content = invocation.getArgument(1);
            uploaded.set(content);
            return new ObjectStorageService.PutObjectResult("etag",
                    (long) content.getBytes(StandardCharsets.UTF_8).length, null);
        });
        when(objectStorage.readBytes(any())).thenAnswer(invocation ->
                uploaded.get().getBytes(StandardCharsets.UTF_8));
        AgentCheckpointService service = service(new ObjectMapper());
        AgentRuntimeState state = AgentRuntimeState.empty(70001L)
                .appendAssistantDraft("x".repeat(300_000), 2L);

        service.checkpointIfNeeded(
                AgentEvent.replay(2L, 70001L, 2L, "context.resolved", "{\"artifactId\":1}"), state);

        ArgumentCaptor<AgentCheckpoint> captor = ArgumentCaptor.forClass(AgentCheckpoint.class);
        verify(checkpointRepository).save(captor.capture());
        assertThat(captor.getValue().stateObjectKey()).contains("agent-runs/70001/checkpoints/");
        assertThat(captor.getValue().stateJson()).isEqualTo("{\"externalState\":true}");
        assertThat(captor.getValue().stateSha256()).hasSize(64);
        verify(checkpointRepository).deleteOlderThanLatest(70001L, 2);
    }

    @Test
    void falls_back_to_previous_checkpoint_when_latest_is_corrupt() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ObjectMapper mapper = new ObjectMapper();
        String validJson = mapper.writeValueAsString(
                AgentRuntimeState.empty(70001L).withStatusAndPhase("RUNNING", "prompt", 5L));
        AgentCheckpoint corrupt = new AgentCheckpoint(
                2L, 70001L, 2L, 8L, "{}", 100, 1, "bad", null, null);
        AgentCheckpoint valid = new AgentCheckpoint(
                1L, 70001L, 1L, 5L, validJson, validJson.getBytes(StandardCharsets.UTF_8).length,
                1, sha256(validJson), null, null);
        when(checkpointRepository.findLatest(70001L, 2)).thenReturn(List.of(corrupt, valid));

        AgentRuntimeState restored = service(mapper).loadLatestFromRedis(70001L);

        assertThat(restored.phase()).isEqualTo("prompt");
        assertThat(restored.lastEventSeq()).isEqualTo(5L);
    }

    private AgentCheckpointService service(ObjectMapper mapper) {
        return new AgentCheckpointService(
                checkpointRepository, redisTemplate, incrementingIds(81000L), mapper, objectStorage);
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private BusinessIdGenerator incrementingIds(long start) {
        AtomicLong next = new AtomicLong(start);
        return next::incrementAndGet;
    }
}
