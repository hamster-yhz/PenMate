package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.agent.usecase.AgentTurnAppService;
import com.penmate.backend.application.agent.usecase.AgentTurnCommand;
import com.penmate.backend.application.agent.usecase.AgentTurnResult;
import com.penmate.backend.application.agent.skill.AgentSkillActivationService;
import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunAppServiceTest {

    @Test
    void create_turn_persists_message_turn_run_input_and_run_started_event() {
        AgentRepository agentRepository = mock(AgentRepository.class);
        AgentSessionRepository sessionRepository = mock(AgentSessionRepository.class);
        AgentRunRepository agentRunRepository = mock(AgentRunRepository.class);
        AgentRunEventPublisher eventPublisher = mock(AgentRunEventPublisher.class);
        AgentRunDispatcher runDispatcher = mock(AgentRunDispatcher.class);
        BusinessIdGenerator idGenerator = ids(60001L, 50001L, 70001L);
        AgentSession session = AgentSession.active(90001L, 101L, 201L, "Run runtime session");
        when(sessionRepository.findSession(101L, 90001L)).thenReturn(session);
        when(sessionRepository.nextTurnSeq(90001L)).thenReturn(1);
        when(agentRepository.nextMessageSeq(90001L)).thenReturn(1);
        when(agentRepository.insertMessage(any(AgentMessage.class))).thenReturn(1);
        when(agentRepository.bindMessageToTurn(90001L, 60001L, 50001L)).thenReturn(1);
        when(agentRepository.touchConversationLastMessage(90001L)).thenReturn(1);
        when(sessionRepository.insertTurn(eq(90001L), eq(50001L), eq(1), eq(60001L), eq(70001L), eq("PENDING"), eq(null))).thenReturn(1);
        when(sessionRepository.updateLastTurn(101L, 90001L, 50001L)).thenReturn(1);
        when(sessionRepository.updateLastRun(101L, 90001L, 70001L)).thenReturn(1);
        when(agentRunRepository.insert(any(AgentRun.class))).thenReturn(1);
        when(agentRunRepository.insertInput(any(AgentRunInput.class))).thenReturn(1);
        when(eventPublisher.publish(eq(70001L), eq("run.started"), any(Map.class)))
                .thenReturn(new AgentEvent(1L, 70001L, 101L, 90001L, 50001L, 1L, 1, "run.started", "{\"schemaVersion\":1}", null));
        AgentTurnAppService service = new AgentTurnAppService(
                new SessionStyleBindingAppService(sessionRepository),
                agentRepository,
                sessionRepository,
                idGenerator,
                new AgentRunAppService(agentRunRepository, eventPublisher, runDispatcher),
                runDispatcher,
                mock(AgentSkillActivationService.class),
                passthroughRecoveryPrompts(),
                mock(com.penmate.backend.application.agent.safety.AgentSafetyModeApplicationService.class)
        );

        AgentTurnResult result = service.createTurn(101L, 90001L, command(), "trace-1");

        assertThat(result.activeRun().runId()).isEqualTo(70001L);
        assertThat(result.activeRun().turnId()).isEqualTo(50001L);
        assertThat(result.activeRun().runStatus()).isEqualTo("running");
        verify(agentRunRepository).insert(any(AgentRun.class));
        verify(agentRunRepository).insertInput(any(AgentRunInput.class));
        verify(eventPublisher).publish(eq(70001L), eq("run.started"), any());
        verify(runDispatcher).dispatchInitialRun(eq(70001L), eq("trace-1"));
    }

    private AgentTurnCommand command() {
        return new AgentTurnCommand(
                201L,
                "Write a suspense opening.",
                java.util.List.of(),
                new AgentTurnCommand.TaskRequest(30001L, java.util.List.of(30001L), 1001L, "selected text")
        );
    }

    private AgentRunRecoveryPromptService passthroughRecoveryPrompts() {
        AgentRunRecoveryPromptService service = mock(AgentRunRecoveryPromptService.class);
        when(service.attachToManualRequest(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
        return service;
    }

    private BusinessIdGenerator ids(Long... ids) {
        return new BusinessIdGenerator() {
            private int index;

            @Override
            public Long nextId() {
                return ids[index++];
            }
        };
    }
}
