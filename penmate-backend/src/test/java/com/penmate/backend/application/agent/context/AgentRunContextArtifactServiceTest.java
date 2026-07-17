package com.penmate.backend.application.agent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.agent.run.model.AgentArtifact;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.application.agent.prompt.PromptPlan;
import com.penmate.backend.domain.agent.run.repository.AgentArtifactRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class AgentRunContextArtifactServiceTest {

    @Test
    void should_select_context_by_type_from_replayed_artifact_refs() throws Exception {
        AgentArtifactRepository artifacts = mock(AgentArtifactRepository.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AgentRunContextArtifactService service = new AgentRunContextArtifactService(
                artifacts, mock(BusinessIdGenerator.class), storage, objectMapper);

        var resolved = new AgentRunContextArtifactService.ResolvedArtifact(
                1, 70001L, 99L, null, null, List.of());
        String content = objectMapper.writeValueAsString(resolved);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(content.getBytes(StandardCharsets.UTF_8)));
        var ref = new AgentRunContextArtifactService.ArtifactRef(88L, "context-key", hash,
                content.getBytes(StandardCharsets.UTF_8).length);
        when(artifacts.findById(89L)).thenReturn(new AgentArtifact(
                89L, 70001L, null, "prompt.composed", "{}", 2, null));
        when(artifacts.findById(88L)).thenReturn(new AgentArtifact(
                88L, 70001L, null, "context.resolved", objectMapper.writeValueAsString(ref), content.length(), null));
        when(artifacts.findLatest(70001L, "context.resolved")).thenReturn(new AgentArtifact(
                88L, 70001L, null, "context.resolved", objectMapper.writeValueAsString(ref), content.length(), null));
        when(storage.readText("context-key")).thenReturn(content);

        var loaded = service.loadContextForRun(70001L, List.of(88L, 89L));

        assertThat(loaded.runId()).isEqualTo(70001L);
        assertThat(loaded.contextEpochId()).isEqualTo(99L);
        assertThat(service.loadLatestContextForRun(70001L)).isEqualTo(loaded);
    }

    @Test
    void should_not_publish_context_metadata_when_object_readback_is_corrupt() {
        AgentArtifactRepository artifacts = mock(AgentArtifactRepository.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        BusinessIdGenerator ids = mock(BusinessIdGenerator.class);
        AgentRunContextArtifactService service = new AgentRunContextArtifactService(
                artifacts, ids, storage, new ObjectMapper().findAndRegisterModules());
        when(ids.nextId()).thenReturn(88L);
        when(storage.putText(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            String content = invocation.getArgument(1);
            return new ObjectStorageService.PutObjectResult("etag",
                    (long) content.getBytes(StandardCharsets.UTF_8).length, null);
        });
        when(storage.readText(anyString())).thenReturn("corrupt");

        assertThatThrownBy(() -> service.save(70001L,
                new AgentRunContextArtifactService.ResolvedArtifact(2, 70001L, 99L, null, null, List.of())))
                .hasMessageContaining("verification failed");

        verify(artifacts, never()).save(any());
    }

    @Test
    void should_round_trip_the_exact_prompt_conversation_snapshot() {
        AgentArtifactRepository artifacts = mock(AgentArtifactRepository.class);
        ObjectStorageService storage = mock(ObjectStorageService.class);
        BusinessIdGenerator ids = mock(BusinessIdGenerator.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        AgentRunContextArtifactService service = new AgentRunContextArtifactService(artifacts, ids, storage, mapper);
        AtomicReference<String> storedContent = new AtomicReference<>();
        when(ids.nextId()).thenReturn(88L);
        when(storage.putText(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            String content = invocation.getArgument(1);
            storedContent.set(content);
            return new ObjectStorageService.PutObjectResult("etag",
                    (long) content.getBytes(StandardCharsets.UTF_8).length, null);
        });
        when(storage.readText(anyString())).thenAnswer(invocation -> storedContent.get());
        List<AgentLlmMessage> messages = List.of(
                AgentLlmMessage.system("stable"), AgentLlmMessage.user("earlier"),
                AgentLlmMessage.assistant("answer", List.of()), AgentLlmMessage.user("current"));

        var ref = service.savePromptPlan(70001L,
                new PromptPlan(List.of(), List.of(), "default", "stable"), null, messages);
        ArgumentCaptor<AgentArtifact> row = ArgumentCaptor.forClass(AgentArtifact.class);
        verify(artifacts).save(row.capture());
        when(artifacts.findById(ref.artifactId())).thenReturn(row.getValue());

        var loaded = service.loadPromptPlan(ref.artifactId());

        assertThat(loaded.schemaVersion()).isEqualTo(2);
        assertThat(loaded.messages()).isEqualTo(messages);
    }
}
