package com.penmate.backend.application.agent.context;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.context.model.AgentRoutingPreference;
import com.penmate.backend.domain.agent.context.repository.AgentRoutingPreferenceRepository;
import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.model.model.ModelConfiguration;
import com.penmate.backend.domain.model.repository.ModelRepository;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class StoryBibleRoutingPreferenceResolver {

    private final AgentRoutingPreferenceRepository preferences;
    private final AgentSessionRepository sessions;
    private final ModelRepository models;
    private final NovelGateway novels;

    public EffectivePreference resolve(Long projectId, Long sessionId, Long userId) {
        requireOwnedSession(projectId, sessionId, userId);
        return resolveProject(projectId, userId);
    }

    public EffectivePreference resolveProject(Long projectId, Long userId) {
        requireOwnedProject(projectId, userId);
        AgentRoutingPreference preference = preferences.findProjectPreference(projectId);
        if (preference == null) {
            return new EffectivePreference(StoryBibleRoutingMode.LLM_SELECTOR, null);
        }
        StoryBibleRoutingMode mode = parseMode(preference.storyBibleRoutingMode());
        boolean activeEmbedding = preference.embeddingModelConfigId() != null
                && "READY".equalsIgnoreCase(preference.indexStatus());
        if (!activeEmbedding && mode != StoryBibleRoutingMode.LLM_SELECTOR) {
            mode = StoryBibleRoutingMode.LLM_SELECTOR;
        }
        validateRouterModel(userId, preference.routerModelConfigId());
        return new EffectivePreference(mode, preference.routerModelConfigId());
    }

    private void requireOwnedSession(Long projectId, Long sessionId, Long userId) {
        AgentSession session = sessions.findSession(projectId, sessionId);
        if (session == null || !Objects.equals(session.getOwnerUserId(), userId)) {
            throw BusinessException.notFound("Agent session not found");
        }
    }

    private void requireOwnedProject(Long projectId, Long userId) {
        NovelProject project = novels.findProjectById(projectId);
        if (project == null || !Objects.equals(project.getOwnerUserId(), userId)) {
            throw BusinessException.notFound("Novel project not found");
        }
    }

    private void validateRouterModel(Long userId, Long modelConfigId) {
        if (modelConfigId == null) return;
        ModelConfiguration config = models.findAccessibleConfiguration(userId, modelConfigId);
        if (config == null || !"CHAT".equals(config.getModelType()) || !"ACTIVE".equalsIgnoreCase(config.getStatus())) {
            throw BusinessException.badRequest("Router model configuration is unavailable");
        }
    }

    private StoryBibleRoutingMode parseMode(String value) {
        try { return StoryBibleRoutingMode.valueOf(value); }
        catch (RuntimeException exception) { throw BusinessException.badRequest("Unsupported Story Bible routing mode"); }
    }

    public record EffectivePreference(StoryBibleRoutingMode mode, Long routerModelConfigId) {
    }
}
