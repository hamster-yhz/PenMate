package com.penmate.backend.application.agent.run;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.agent.run.model.AgentArtifact;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.repository.AgentArtifactRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Service
public class AgentEventPayloadResolver {

    private final AgentArtifactRepository artifacts;
    private final ObjectMapper objectMapper;

    public AgentEventPayloadResolver(AgentArtifactRepository artifacts, ObjectMapper objectMapper) {
        this.artifacts = artifacts;
        this.objectMapper = objectMapper;
    }

    public AgentEvent resolve(AgentEvent event) {
        String payload = event.payloadJson();
        if (payload == null || !payload.contains("artifactRef")) {
            return event;
        }
        try {
            JsonNode envelope = objectMapper.readTree(payload);
            JsonNode reference = envelope.get("artifactRef");
            if (reference == null || reference.isNull()) {
                return event;
            }
            Long artifactId = reference.asLong();
            if (artifactId <= 0) {
                throw new IllegalStateException("Agent Event payload artifact reference is invalid");
            }
            AgentArtifact artifact = artifacts.findById(artifactId);
            if (artifact == null) {
                throw new IllegalStateException("Agent Event payload artifact not found: " + artifactId);
            }
            if (!Objects.equals(event.runId(), artifact.runId())) {
                throw new IllegalStateException("Agent Event payload artifact belongs to another run");
            }
            if (!Objects.equals(event.eventType(), artifact.artifactType())) {
                throw new IllegalStateException("Agent Event payload artifact type mismatch");
            }
            String resolved = artifact.payloadJson();
            if (resolved == null) {
                throw new IllegalStateException("Agent Event payload artifact is empty");
            }
            int actualSize = resolved.getBytes(StandardCharsets.UTF_8).length;
            if (artifact.sizeBytes() == null || artifact.sizeBytes() != actualSize) {
                throw new IllegalStateException("Agent Event payload artifact size mismatch");
            }
            JsonNode declaredSize = envelope.get("sizeBytes");
            if (declaredSize != null && declaredSize.asInt(-1) != actualSize) {
                throw new IllegalStateException("Agent Event payload reference size mismatch");
            }
            return AgentEvent.forReplay(event, resolved);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid Agent Event artifact reference", ex);
        }
    }
}
