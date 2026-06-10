package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.agent.run.AgentRunAppService;
import com.penmate.backend.application.agent.run.AgentRunCommand;
import com.penmate.backend.application.agent.run.AgentRunResult;
import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTurnAppServiceTest {

    @Test
    void should_create_turn_message_and_run_pipeline() {
        AgentRepository agentRepository = agentRepository();
        AgentSessionRepository sessionRepository = sessionRepository();
        AgentRunAppService runAppService = mock(AgentRunAppService.class);
        when(runAppService.createRun(any(AgentRunCommand.class)))
                .thenReturn(new AgentRunResult(950001L, "running", "created", 7L));
        AgentTurnAppService service = new AgentTurnAppService(
                new SessionStyleBindingAppService(sessionRepository),
                agentRepository,
                sessionRepository,
                businessIdGenerator(930001L, 940001L, 950001L),
                runAppService
        );

        AgentTurnResult result = service.createTurn(920001L, 920002L, command(), "trace-turn-1");

        assertThat(result.taskType()).isEqualTo("WRITE");
        assertThat(result.userMessage()).isEqualTo("Continue the scene.");
        assertThat(result.activeRun()).isNotNull();
        assertThat(result.activeRun().turnId()).isEqualTo(940001L);
        assertThat(result.activeRun().runId()).isEqualTo(950001L);
        assertThat(result.activeRun().runStatus()).isEqualTo("running");
        assertThat(result.activeRun().runPhase()).isEqualTo("created");
        assertThat(result.activeRun().latestSequence()).isEqualTo(7L);
        verify(agentRepository).insertMessage(any(AgentMessage.class));
        verify(sessionRepository).insertTurn(920002L, 940001L, 1, 930001L, 950001L, "pending", null);
        verify(sessionRepository).updateLastTurn(920001L, 920002L, 940001L);
        verify(sessionRepository).updateLastRun(920001L, 920002L, 950001L);
    }

    @Test
    void should_pass_style_model_and_context_inputs_to_run_service() {
        AgentSessionRepository sessionRepository = sessionRepository();
        AgentRunAppService runAppService = mock(AgentRunAppService.class);
        when(runAppService.createRun(any(AgentRunCommand.class)))
                .thenReturn(new AgentRunResult(950002L, "running", "created", 1L));
        AgentTurnAppService service = new AgentTurnAppService(
                new SessionStyleBindingAppService(sessionRepository),
                agentRepository(),
                sessionRepository,
                businessIdGenerator(930002L, 940002L, 950002L),
                runAppService
        );

        service.createTurn(920001L, 920002L, command(), "trace-turn-style");

        ArgumentCaptor<AgentRunCommand> captor = ArgumentCaptor.forClass(AgentRunCommand.class);
        verify(runAppService).createRun(captor.capture());
        AgentRunCommand runCommand = captor.getValue();
        assertThat(runCommand.projectId()).isEqualTo(920001L);
        assertThat(runCommand.sessionId()).isEqualTo(920002L);
        assertThat(runCommand.turnId()).isEqualTo(940002L);
        assertThat(runCommand.runId()).isEqualTo(950002L);
        assertThat(runCommand.ownerUserId()).isEqualTo(1001L);
        assertThat(runCommand.taskType()).isEqualTo("WRITE");
        assertThat(runCommand.promptSnapshot()).isEqualTo("Continue the scene.");
        assertThat(runCommand.chapterId()).isEqualTo(3001L);
        assertThat(runCommand.selectedText()).isEqualTo("selected text");
        assertThat(runCommand.styleSnapshotJson()).isEqualTo("{\"styleId\":81}");
        assertThat(runCommand.modelSnapshotJson()).contains("\"operatorId\":1001", "\"modelConfigId\":4001");
        assertThat(runCommand.inputHash()).isNotBlank();
        assertThat(runCommand.traceId()).isEqualTo("trace-turn-style");
    }

    @Test
    void should_return_pending_run_view_when_run_service_is_not_injected() {
        AgentTurnAppService service = new AgentTurnAppService(
                new SessionStyleBindingAppService(sessionRepository()),
                agentRepository(),
                sessionRepository(),
                businessIdGenerator(930003L, 940003L, 950003L)
        );

        AgentTurnResult result = service.createTurn(920001L, 920002L, command(), "trace-turn-fallback");

        assertThat(result.activeRun().turnId()).isEqualTo(940003L);
        assertThat(result.activeRun().runId()).isEqualTo(950003L);
        assertThat(result.activeRun().runStatus()).isEqualTo("pending");
        assertThat(result.activeRun().runPhase()).isEqualTo("created");
        assertThat(result.activeRun().latestSequence()).isZero();
    }

    private static AgentTurnCommand command() {
        return new AgentTurnCommand(
                1001L,
                "Continue the scene.",
                new AgentTurnCommand.TaskRequest("WRITE", 3001L, 4001L, "selected text")
        );
    }

    private static BusinessIdGenerator businessIdGenerator(Long... ids) {
        BusinessIdGenerator generator = mock(BusinessIdGenerator.class);
        if (ids != null && ids.length > 0) {
            Long first = ids[0];
            Long[] rest = java.util.Arrays.copyOfRange(ids, 1, ids.length);
            when(generator.nextId()).thenReturn(first, rest);
        }
        return generator;
    }

    private static AgentRepository agentRepository() {
        AgentRepository repository = mock(AgentRepository.class);
        when(repository.nextMessageSeq(920002L)).thenReturn(1);
        when(repository.insertMessage(any(AgentMessage.class))).thenReturn(1);
        when(repository.touchConversationLastMessage(920002L)).thenReturn(1);
        return repository;
    }

    private static AgentSessionRepository sessionRepository() {
        AgentSessionRepository repository = mock(AgentSessionRepository.class);
        AgentSession session = AgentSession.active(920002L, 920001L, 1001L, "Session-A");
        session.bindStyle(81L);
        when(repository.findSession(920001L, 920002L)).thenReturn(session);
        when(repository.nextTurnSeq(920002L)).thenReturn(1);
        when(repository.insertTurn(eq(920002L), any(Long.class), eq(1), any(Long.class), any(Long.class), eq("pending"), eq(null))).thenReturn(1);
        when(repository.updateLastTurn(eq(920001L), eq(920002L), any(Long.class))).thenReturn(1);
        when(repository.updateLastRun(eq(920001L), eq(920002L), any(Long.class))).thenReturn(1);
        return repository;
    }
}
