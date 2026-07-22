package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.domain.agent.run.model.AgentArtifact;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.repository.AgentArtifactRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

@Service
public class AgentEventPayloadResolver {

    private final AgentArtifactRepository artifacts;
    private final JsonCodec jsonCodec;

    public AgentEventPayloadResolver(AgentArtifactRepository artifacts, JsonCodec jsonCodec) {
        this.artifacts = artifacts;
        this.jsonCodec = jsonCodec;
    }

    public AgentEvent resolve(AgentEvent event) {
        String payload = event.payloadJson();
        if (payload == null || !payload.contains("artifactRef")) {
            return event;
        }
        try {
            Map<String, Object> envelope = jsonCodec.readObject(payload);
            Object reference = envelope.get("artifactRef");
            if (reference == null) {
                return event;
            }
            long artifactId = parseLong(reference, -1L);
            if (artifactId <= 0L) {
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
            Object declaredSize = envelope.get("sizeBytes");
            if (declaredSize != null && parseLong(declaredSize, -1L) != actualSize) {
                throw new IllegalStateException("Agent Event payload reference size mismatch");
            }
            return AgentEvent.forReplay(event, resolved);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid Agent Event artifact reference", ex);
        }
    }

    private long parseLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
