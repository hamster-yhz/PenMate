package com.penmate.backend.application.agent.context;

import com.penmate.backend.domain.agent.context.model.AgentRoutingPreference;
import com.penmate.backend.domain.agent.context.repository.AgentRoutingPreferenceRepository;
import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.model.repository.ModelRepository;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StoryBibleRoutingPreferenceResolverTest {
    private final AgentRoutingPreferenceRepository preferences = mock(AgentRoutingPreferenceRepository.class);
    private final AgentSessionRepository sessions = mock(AgentSessionRepository.class);
    private final ModelRepository models = mock(ModelRepository.class);
    private final StoryBibleRoutingPreferenceResolver resolver =
            new StoryBibleRoutingPreferenceResolver(preferences, sessions, models);

    @Test
    void should_resolve_session_override_then_user_then_default() {
        AgentSession session = AgentSession.active(30L, 20L, 9L, "Draft");
        when(sessions.findSession(20L, 30L)).thenReturn(session);
        when(preferences.findUserPreference(9L)).thenReturn(
                new AgentRoutingPreference(9L, "LLM_SELECTOR", 70L));
        Instant updated = Instant.parse("2026-07-16T00:00:00Z");
        when(models.findUserModelConfig(9L, 70L)).thenReturn(
                Map.of("status", "ACTIVE", "updatedAt", Timestamp.from(updated)));

        var effective = resolver.resolve(20L, 30L, 9L);

        assertThat(effective.mode()).isEqualTo(StoryBibleRoutingMode.LLM_SELECTOR);
        assertThat(effective.routerModelConfigId()).isEqualTo(70L);
        assertThat(effective.routerModelConfigRevision()).isEqualTo(updated.toEpochMilli());
        assertThat(effective.sessionOverride()).isFalse();
    }

    @Test
    void should_use_default_when_no_preference_exists() {
        when(sessions.findSession(20L, 30L)).thenReturn(AgentSession.active(30L, 20L, 9L, "Draft"));
        assertThat(resolver.resolve(20L, 30L, 9L).mode())
                .isEqualTo(StoryBibleRoutingMode.RETRIEVAL_THEN_LLM);
    }
}
