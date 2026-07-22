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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoryBibleRoutingPreferenceResolverTest {
    private final AgentRoutingPreferenceRepository preferences = mock(AgentRoutingPreferenceRepository.class);
    private final AgentSessionRepository sessions = mock(AgentSessionRepository.class);
    private final ModelRepository models = mock(ModelRepository.class);
    private final NovelGateway novels = mock(NovelGateway.class);
    private final StoryBibleRoutingPreferenceResolver resolver =
            new StoryBibleRoutingPreferenceResolver(preferences, sessions, models, novels);

    @Test
    void resolvesProjectPreferenceForOwnedSession() {
        when(sessions.findSession(20L, 30L)).thenReturn(AgentSession.active(30L, 20L, 9L, "Draft"));
        when(novels.findProjectById(20L)).thenReturn(project(20L, 9L));
        when(preferences.findProjectPreference(20L)).thenReturn(
                new AgentRoutingPreference(20L, "RETRIEVAL_THEN_LLM", 70L, 80L, "READY"));
        ModelConfiguration router = new ModelConfiguration();
        router.setModelType("CHAT");
        router.setStatus("ACTIVE");
        when(models.findAccessibleConfiguration(9L, 70L)).thenReturn(router);

        var effective = resolver.resolve(20L, 30L, 9L);

        assertThat(effective.mode()).isEqualTo(StoryBibleRoutingMode.RETRIEVAL_THEN_LLM);
        assertThat(effective.routerModelConfigId()).isEqualTo(70L);
    }

    @Test
    void forcesLlmSelectorWhenEmbeddingIndexIsUnavailable() {
        when(novels.findProjectById(20L)).thenReturn(project(20L, 9L));
        when(preferences.findProjectPreference(20L)).thenReturn(
                new AgentRoutingPreference(20L, "RETRIEVAL", null, 80L, "REINDEX_REQUIRED"));

        assertThat(resolver.resolveProject(20L, 9L).mode()).isEqualTo(StoryBibleRoutingMode.LLM_SELECTOR);
    }

    @Test
    void rejectsSessionOwnedByAnotherUser() {
        when(sessions.findSession(20L, 30L)).thenReturn(AgentSession.active(30L, 20L, 8L, "Draft"));

        assertThatThrownBy(() -> resolver.resolve(20L, 30L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Agent session not found");
    }

    private NovelProject project(Long projectId, Long ownerUserId) {
        NovelProject project = new NovelProject();
        project.setProjectId(projectId);
        project.setOwnerUserId(ownerUserId);
        return project;
    }
}
