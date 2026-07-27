package com.penmate.backend.application.agent.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.run.model.AgentArtifact;
import com.penmate.backend.domain.agent.run.model.AgentRunContinuation;
import com.penmate.backend.domain.agent.run.model.AgentRunNoProgressState;
import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;
import com.penmate.backend.domain.agent.run.repository.AgentArtifactRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AgentRunContinuationArtifactServiceTest {

    @Test
    void saves_verifies_and_loads_only_a_referenced_run_continuation() {
        AgentArtifactRepository artifacts = mock(AgentArtifactRepository.class);
        BusinessIdGenerator ids = mock(BusinessIdGenerator.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AgentRunContinuationArtifactService service = new AgentRunContinuationArtifactService(
                artifacts, ids, storage, new JacksonJsonCodec(objectMapper));
        AtomicReference<String> storedJson = new AtomicReference<>();
        when(ids.nextId()).thenReturn(88001L);
        when(storage.putText(any(), any(), any())).thenAnswer(invocation -> {
            String json = invocation.getArgument(1);
            storedJson.set(json);
            return new ObjectStorageService.PutObjectResult(
                    invocation.getArgument(0), (long) json.getBytes(StandardCharsets.UTF_8).length, null);
        });
        when(storage.readBytes(any())).thenAnswer(invocation ->
                storedJson.get().getBytes(StandardCharsets.UTF_8));

        AgentRunNoProgressState noProgressState = AgentRunNoProgressState.EMPTY.append(
                "story_bible_node_write\nnonce=1", java.util.Set.of("nodeId=71"), false);
        AgentRunContinuation continuation = AgentRunContinuation.readyForTool(
                70001L,
                List.of(AgentLlmMessage.user("write")),
                2, 1, 0, "draft", new LlmTokenUsage(3, 2, 5), noProgressState);
        AgentRunContinuationArtifactService.ArtifactRef ref = service.save(continuation);

        ArgumentCaptor<AgentArtifact> row = ArgumentCaptor.forClass(AgentArtifact.class);
        verify(artifacts).save(row.capture());
        when(artifacts.findById(88001L)).thenReturn(row.getValue());
        assertThat(service.loadLatestForRun(70001L, List.of(999L, 88001L)))
                .contains(continuation);
        assertThat(ref.objectKey()).isEqualTo("agent-runs/70001/continuations/88001.json");
        assertThat(ref.sha256()).hasSize(64);
    }

    @Test
    void does_not_publish_metadata_when_object_readback_is_corrupt() {
        AgentArtifactRepository artifacts = mock(AgentArtifactRepository.class);
        BusinessIdGenerator ids = mock(BusinessIdGenerator.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        AgentRunContinuationArtifactService service = new AgentRunContinuationArtifactService(
                artifacts, ids, storage,
                new JacksonJsonCodec(new ObjectMapper().findAndRegisterModules()));
        when(ids.nextId()).thenReturn(88002L);
        when(storage.putText(any(), any(), any())).thenAnswer(invocation -> {
            String json = invocation.getArgument(1);
            return new ObjectStorageService.PutObjectResult("etag",
                    (long) json.getBytes(StandardCharsets.UTF_8).length, null);
        });
        when(storage.readBytes(any())).thenReturn("corrupt".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.save(AgentRunContinuation.readyForLlm(
                70001L, List.of(), 1, 0, "", LlmTokenUsage.ZERO)))
                .hasMessageContaining("integrity");
        verify(artifacts, never()).save(any());
    }
}
