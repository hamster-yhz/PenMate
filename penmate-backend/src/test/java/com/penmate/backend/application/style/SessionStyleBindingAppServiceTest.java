package com.penmate.backend.application.style;

import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionStyleBindingAppServiceTest {

    @Mock
    private AgentSessionRepository agentSessionRepository;

    @InjectMocks
    private SessionStyleBindingAppService bindingAppService;

    @Test
    void should_bind_style_to_session_via_repository_and_expose_persisted_snapshot() {
        AgentSession session = AgentSession.active(90001L, 101L, 201L, "Session-A");
        when(agentSessionRepository.findSession(101L, 90001L)).thenAnswer(invocation -> {
            session.bindStyle(81L);
            return session;
        });
        when(agentSessionRepository.updateBoundStyle(101L, 90001L, 81L, 201L)).thenReturn(1);
        when(agentSessionRepository.insertStyleBinding(101L, 90001L, 81L, 201L, "trace-1")).thenReturn(1);

        bindingAppService.bind(101L, 90001L, 81L, 201L, "trace-1");

        ArgumentCaptor<Long> styleCaptor = ArgumentCaptor.forClass(Long.class);
        verify(agentSessionRepository).updateBoundStyle(101L, 90001L, 81L, 201L);
        verify(agentSessionRepository).deactivateStyleBindings(90001L);
        verify(agentSessionRepository).insertStyleBinding(
                eq(101L),
                eq(90001L),
                styleCaptor.capture(),
                eq(201L),
                eq("trace-1")
        );
        assertThat(styleCaptor.getValue()).isEqualTo(81L);
        assertThat(bindingAppService.getBoundStyleId(101L, 90001L)).isEqualTo(81L);
        assertThat(bindingAppService.getBoundStyleSnapshotJson(101L, 90001L)).isEqualTo("{\"styleId\":81}");
    }

    @Test
    void should_fail_when_updating_session_bound_style_does_not_affect_exactly_one_row() {
        when(agentSessionRepository.updateBoundStyle(101L, 90001L, 81L, 201L)).thenReturn(0);

        assertThatThrownBy(() -> bindingAppService.bind(101L, 90001L, 81L, 201L, "trace-fail-update"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("update session bound style");
    }

    @Test
    void should_fail_when_inserting_style_binding_history_does_not_affect_exactly_one_row() {
        when(agentSessionRepository.updateBoundStyle(101L, 90001L, 81L, 201L)).thenReturn(1);
        when(agentSessionRepository.insertStyleBinding(101L, 90001L, 81L, 201L, "trace-fail-history")).thenReturn(0);

        assertThatThrownBy(() -> bindingAppService.bind(101L, 90001L, 81L, 201L, "trace-fail-history"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insert style binding history");
    }
}
