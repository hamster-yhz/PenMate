package com.penmate.backend.application.agent.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.agent.run.model.AgentArtifact;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.repository.AgentArtifactRepository;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentEventPayloadResolverTest {

    @Test
    void rejects_artifact_from_another_run() {
        AgentArtifactRepository artifacts = mock(AgentArtifactRepository.class);
        String payload = "{\"text\":\"answer\"}";
        when(artifacts.findById(88L)).thenReturn(artifact(71L, payload, payload.length()));

        assertThatThrownBy(() -> new AgentEventPayloadResolver(artifacts, new ObjectMapper())
                .resolve(referenceEvent()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("another run");
    }

    @Test
    void rejects_artifact_with_invalid_size_manifest() {
        AgentArtifactRepository artifacts = mock(AgentArtifactRepository.class);
        String payload = "{\"text\":\"answer\"}";
        when(artifacts.findById(88L)).thenReturn(artifact(70L, payload, 1));

        assertThatThrownBy(() -> new AgentEventPayloadResolver(artifacts, new ObjectMapper())
                .resolve(referenceEvent()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("size mismatch");
    }

    private AgentEvent referenceEvent() {
        return AgentEvent.replay(1L, 70L, 1L, "message.completed", "{\"artifactRef\":\"88\"}");
    }

    private AgentArtifact artifact(Long runId, String payload, int size) {
        return new AgentArtifact(88L, runId, null, "message.completed", payload,
                size == payload.length() ? payload.getBytes(StandardCharsets.UTF_8).length : size, null);
    }
}
