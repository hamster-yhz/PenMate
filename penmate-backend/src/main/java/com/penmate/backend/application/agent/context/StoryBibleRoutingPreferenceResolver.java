package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.context.model.AgentRoutingPreference;
import com.penmate.backend.domain.agent.context.repository.AgentRoutingPreferenceRepository;
import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.model.repository.ModelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Service
public class StoryBibleRoutingPreferenceResolver {

    private final AgentRoutingPreferenceRepository preferences;
    private final AgentSessionRepository sessions;
    private final ModelRepository models;

    public StoryBibleRoutingPreferenceResolver(AgentRoutingPreferenceRepository preferences,
                                                AgentSessionRepository sessions,
                                                ModelRepository models) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.models = Objects.requireNonNull(models, "models");
    }

    public EffectivePreference resolve(Long projectId, Long sessionId, Long userId) {
        AgentSession session = sessions.findSession(projectId, sessionId);
        if (session == null || !Objects.equals(session.getOwnerUserId(), userId)) {
            throw BusinessException.notFound("Agent session not found");
        }
        AgentRoutingPreference user = preferences.findUserPreference(userId);
        String rawMode = firstNonBlank(session.getStoryBibleRoutingMode(),
                user == null ? null : user.storyBibleRoutingMode(), StoryBibleRoutingMode.RETRIEVAL_THEN_LLM.name());
        StoryBibleRoutingMode mode = parseMode(rawMode);
        Long modelId = session.getRouterModelConfigId() != null
                ? session.getRouterModelConfigId() : user == null ? null : user.routerModelConfigId();
        long modelRevision = validateModel(userId, modelId);
        return new EffectivePreference(mode, modelId, modelRevision,
                session.getStoryBibleRoutingMode() != null || session.getRouterModelConfigId() != null);
    }

    public EffectivePreference getUserDefault(Long userId) {
        AgentRoutingPreference user = preferences.findUserPreference(userId);
        StoryBibleRoutingMode mode = parseMode(user == null || user.storyBibleRoutingMode() == null
                ? StoryBibleRoutingMode.RETRIEVAL_THEN_LLM.name() : user.storyBibleRoutingMode());
        Long modelId = user == null ? null : user.routerModelConfigId();
        return new EffectivePreference(mode, modelId, validateModel(userId, modelId), false);
    }

    @Transactional
    public void saveUserDefault(Long userId, StoryBibleRoutingMode mode, Long modelConfigId) {
        validateModel(userId, modelConfigId);
        if (preferences.upsertUserPreference(new AgentRoutingPreference(userId,
                Objects.requireNonNull(mode, "mode").name(), modelConfigId)) != 1) {
            throw BusinessException.of("Failed to save Story Bible routing preference");
        }
    }

    @Transactional
    public void saveSessionOverride(Long projectId, Long sessionId, Long userId,
                                    StoryBibleRoutingMode mode, Long modelConfigId) {
        AgentSession session = sessions.findSession(projectId, sessionId);
        if (session == null || !Objects.equals(session.getOwnerUserId(), userId)) {
            throw BusinessException.notFound("Agent session not found");
        }
        validateModel(userId, modelConfigId);
        if (preferences.updateSessionOverride(projectId, sessionId, mode == null ? null : mode.name(), modelConfigId) != 1) {
            throw BusinessException.of("Failed to save session Story Bible routing override");
        }
    }

    private long validateModel(Long userId, Long modelConfigId) {
        if (modelConfigId == null) return 0L;
        Map<String, Object> config = models.findUserModelConfig(userId, modelConfigId);
        if (config == null || !"ACTIVE".equalsIgnoreCase(String.valueOf(config.get("status")))) {
            throw BusinessException.badRequest("Router model configuration is unavailable");
        }
        Object updatedAt = config.get("updatedAt");
        if (updatedAt instanceof Timestamp timestamp) return timestamp.toInstant().toEpochMilli();
        if (updatedAt instanceof Instant dateTime) return dateTime.toEpochMilli();
        return 0L;
    }

    private StoryBibleRoutingMode parseMode(String value) {
        try {
            return StoryBibleRoutingMode.valueOf(value);
        } catch (RuntimeException ex) {
            throw BusinessException.badRequest("Unsupported Story Bible routing mode");
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        throw new IllegalStateException("Routing mode fallback missing");
    }

    public record EffectivePreference(StoryBibleRoutingMode mode, Long routerModelConfigId,
                                      long routerModelConfigRevision, boolean sessionOverride) {
    }
}
