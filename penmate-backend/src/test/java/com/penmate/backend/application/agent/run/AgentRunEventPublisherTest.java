package com.penmate.backend.application.agent.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.agent.run.repository.AgentArtifactRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunEventRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunProjectionRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;

@ExtendWith(MockitoExtension.class)
class AgentRunEventPublisherTest {

    @Mock
    private AgentRunEventRepository eventRepository;
    @Mock
    private AgentProjectionUpdater projectionUpdater;
    @Mock
    private AgentRunEventBus eventBus;
    @Mock
    private AgentRunProjectionRepository runProjectionRepository;
    @Mock
    private AgentArtifactRepository artifactRepository;
    @Mock
    private BusinessIdGenerator businessIdGenerator;
    @Mock
    private AgentSessionRepository agentSessionRepository;

    @Test
    void publish_appends_event_updates_projection_and_broadcasts_after_commit() {
        AgentEvent appended = new AgentEvent(1L, 70001L, 101L, 90001L, 50001L, 1L, 1,
                "run.started", "{\"schemaVersion\":1,\"phase\":\"created\"}", null);
        when(eventRepository.append(70001L, "run.started", "{\"schemaVersion\":1,\"phase\":\"created\"}"))
                .thenReturn(appended);
        AgentRunEventPublisher publisher = new AgentRunEventPublisher(
                eventRepository,
                projectionUpdater,
                eventBus,
                new JacksonJsonCodec(new ObjectMapper()),
                artifactRepository,
                businessIdGenerator
        );

        AgentEvent result = publisher.publish(70001L, "run.started", Map.of("phase", "created"));

        assertThat(result.sequence()).isEqualTo(1L);
        verify(projectionUpdater).apply(appended);
        verify(eventBus).publish(appended);
    }

    @Test
    void projection_ignores_events_at_or_below_latest_applied_sequence() {
        when(runProjectionRepository.findLatestSequence(70001L)).thenReturn(5L);
        AgentProjectionUpdater updater = new AgentProjectionUpdater(
                runProjectionRepository,
                agentSessionRepository,
                businessIdGenerator,
                new JacksonJsonCodec(new ObjectMapper()),
                payloadResolver()
        );

        updater.apply(event(70001L, 5L, "message.delta", Map.of("text", "abc")));

        verify(runProjectionRepository, never()).appendAssistantDelta(any(), any(), any());
    }

    @Test
    void projection_persists_completed_assistant_message_without_writing_error_fields() {
        String longAssistantText = "我可以帮助你使用写作、改稿、角色设计、世界观整理、工具调用审批等能力。".repeat(20);
        when(runProjectionRepository.findLatestSequence(70001L)).thenReturn(8L);
        when(businessIdGenerator.nextId()).thenReturn(99001L);
        when(agentSessionRepository.nextMessageSeq(90001L)).thenReturn(4);
        when(agentSessionRepository.insertSessionMessage(90001L, 50001L, 99001L,
                "assistant", "CHAT", longAssistantText, 4)).thenReturn(1);

        AgentProjectionUpdater updater = new AgentProjectionUpdater(
                runProjectionRepository,
                agentSessionRepository,
                businessIdGenerator,
                new JacksonJsonCodec(new ObjectMapper()),
                payloadResolver()
        );

        updater.apply(event(70001L, 9L, "message.completed", Map.of(
                "role", "assistant",
                "text", longAssistantText
        )));

        verify(agentSessionRepository).insertSessionMessage(90001L, 50001L, 99001L,
                "assistant", "CHAT", longAssistantText, 4);
        verify(agentSessionRepository).updateTurnAssistantMessage(90001L, 50001L, 99001L);
        verify(runProjectionRepository).setCurrentAssistantMessage(70001L, 99001L, 9L);
        verify(runProjectionRepository, never()).updateRunState(eq(70001L), any(), any(), any(), any(), anyString(), any());
    }

    @Test
    void projection_reuses_existing_completed_assistant_message_for_same_turn() {
        when(runProjectionRepository.findLatestSequence(70001L)).thenReturn(8L);
        when(agentSessionRepository.findTurnAssistantMessageId(90001L, 50001L)).thenReturn(99001L);
        when(agentSessionRepository.updateMessageContent(90001L, 99001L, "already persisted")).thenReturn(1);

        AgentProjectionUpdater updater = new AgentProjectionUpdater(
                runProjectionRepository,
                agentSessionRepository,
                businessIdGenerator,
                new JacksonJsonCodec(new ObjectMapper()),
                payloadResolver()
        );

        updater.apply(event(70001L, 9L, "message.completed", Map.of(
                "role", "assistant",
                "text", "already persisted"
        )));

        verify(agentSessionRepository, never()).insertSessionMessage(any(), any(), any(), any(), any(), any(), any());
        verify(agentSessionRepository).updateMessageContent(90001L, 99001L, "already persisted");
        verify(runProjectionRepository).setCurrentAssistantMessage(70001L, 99001L, 9L);
    }

    @Test
    void projection_bounds_error_fields_for_failed_runs() {
        when(runProjectionRepository.findLatestSequence(70001L)).thenReturn(8L);
        AgentProjectionUpdater updater = new AgentProjectionUpdater(
                runProjectionRepository,
                agentSessionRepository,
                businessIdGenerator,
                new JacksonJsonCodec(new ObjectMapper()),
                payloadResolver()
        );

        updater.apply(event(70001L, 9L, "run.failed", Map.of(
                "errorCode", "provider_error_".repeat(20),
                "errorMessage", "模型供应商返回了一个非常长的错误消息。".repeat(80)
        )));

        ArgumentCaptor<String> errorCodeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> errorMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(runProjectionRepository).updateRunState(eq(70001L), eq("FAILED"), eq("failed"), eq(null), eq(9L),
                errorCodeCaptor.capture(), errorMessageCaptor.capture());
        assertThat(errorCodeCaptor.getValue()).hasSizeLessThanOrEqualTo(96);
        assertThat(errorMessageCaptor.getValue()).hasSizeLessThanOrEqualTo(500);
    }

    @Test
    void projection_resolves_large_completed_message_artifact_before_persisting_history() {
        String text = "x".repeat(70_000);
        String payload = "{\"schemaVersion\":1,\"role\":\"assistant\",\"text\":\"" + text + "\"}";
        when(runProjectionRepository.findLatestSequence(70001L)).thenReturn(8L);
        when(artifactRepository.findById(88001L)).thenReturn(
                new com.penmate.backend.domain.agent.run.model.AgentArtifact(
                        88001L, 70001L, null, "message.completed", payload,
                        payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, null));
        when(businessIdGenerator.nextId()).thenReturn(99001L);
        when(agentSessionRepository.nextMessageSeq(90001L)).thenReturn(4);
        when(agentSessionRepository.insertSessionMessage(
                90001L, 50001L, 99001L, "assistant", "CHAT", text, 4)).thenReturn(1);
        AgentProjectionUpdater updater = new AgentProjectionUpdater(
                runProjectionRepository, agentSessionRepository, businessIdGenerator,
                new JacksonJsonCodec(new ObjectMapper()), payloadResolver());
        AgentEvent event = new AgentEvent(
                9L, 70001L, 101L, 90001L, 50001L, 9L, 1, "message.completed",
                "{\"artifactRef\":\"88001\",\"sizeBytes\":" + payload.length() + "}", null);

        updater.apply(event);

        verify(agentSessionRepository).insertSessionMessage(
                90001L, 50001L, 99001L, "assistant", "CHAT", text, 4);
        verify(runProjectionRepository).setCurrentAssistantMessage(70001L, 99001L, 9L);
    }

    private AgentEvent event(Long runId, Long sequence, String eventType, Map<String, Object> payload) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return new AgentEvent(sequence, runId, 101L, 90001L, 50001L, sequence, 1, eventType,
                    objectMapper.writeValueAsString(payload), null);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private AgentEventPayloadResolver payloadResolver() {
        return new AgentEventPayloadResolver(
                artifactRepository, new JacksonJsonCodec(new ObjectMapper()));
    }
}
