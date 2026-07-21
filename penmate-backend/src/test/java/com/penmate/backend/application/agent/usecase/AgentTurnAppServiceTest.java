package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.agent.run.*;
import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTurnAppServiceTest {

    private static final Long FAKE_RUN_ID = 999001L;
    private static final Long FAKE_TURN_ID = 998001L;
    private static final Long FAKE_USER_MESSAGE_ID = 997001L;

    @Test
    void should_create_turn_and_dispatch_run() {
        AgentRunAppService runAppService = runAppServiceThatSucceeds();
        AsyncAgentRunDispatcher runDispatcher = mock(AsyncAgentRunDispatcher.class);
        AgentTurnAppService agentTurnAppService = new AgentTurnAppService(
                mock(SessionStyleBindingAppService.class),
                agentRepository(),
                sessionRepository(),
                businessIdGenerator(FAKE_USER_MESSAGE_ID, FAKE_TURN_ID, FAKE_RUN_ID),
                runAppService,
                runDispatcher
        );

        Long projectId = 920001L;
        Long sessionId = AgentSession.active(920002L, projectId, 1001L, "Session-A").getSessionId();
        AgentTurnCommand command = new AgentTurnCommand(
                1001L,
                "write a chapter",
                new AgentTurnCommand.TaskRequest("WRITE", 3001L, null, "selected text")
        );

        AgentTurnResult result = agentTurnAppService.createTurn(projectId, sessionId, command, "trace-turn-1");

        assertThat(result.activeRun()).isNotNull();
        assertThat(result.sessionId()).isEqualTo(sessionId);
        assertThat(result.activeRun().turnId()).isEqualTo(FAKE_TURN_ID);
        assertThat(result.activeRun().runId()).isEqualTo(FAKE_RUN_ID);
        assertThat(result.activeRun().runStatus()).isEqualTo("running");
        assertThat(result.activeRun().runPhase()).isEqualTo("created");
        assertThat(result.activeRun().latestSequence()).isEqualTo(1L);
    }

    @Test
    void should_include_turn_id_in_result_active_run() {
        AgentRunAppService runAppService = runAppServiceThatSucceeds();
        AsyncAgentRunDispatcher runDispatcher = mock(AsyncAgentRunDispatcher.class);
        AgentTurnAppService agentTurnAppService = new AgentTurnAppService(
                mock(SessionStyleBindingAppService.class),
                agentRepository(),
                sessionRepository(),
                businessIdGenerator(FAKE_USER_MESSAGE_ID, FAKE_TURN_ID, FAKE_RUN_ID),
                runAppService,
                runDispatcher
        );

        AgentTurnResult result = agentTurnAppService.createTurn(
                920001L,
                920002L,
                new AgentTurnCommand(1001L, "hello", new AgentTurnCommand.TaskRequest("WRITE", null, null, null)),
                "trace-2"
        );

        assertThat(result.activeRun().runId()).isNotNull();
        assertThat(result.activeRun().turnId()).isEqualTo(FAKE_TURN_ID);
        assertThat(result.activeRun().runStatus()).isEqualTo("running");
    }

    private static AgentRunAppService runAppServiceThatSucceeds() {
        AgentRunAppService service = mock(AgentRunAppService.class);
        when(service.createRun(any())).thenReturn(new AgentRunResult(FAKE_RUN_ID, "running", "created", 1L));
        return service;
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
        when(repository.insertMessage(any())).thenReturn(1);
        when(repository.bindMessageToTurn(any(), any(), any())).thenReturn(1);
        when(repository.touchConversationLastMessage(920002L)).thenReturn(1);
        return repository;
    }

    private static AgentSessionRepository sessionRepository() {
        AgentSessionRepository repository = mock(AgentSessionRepository.class);
        when(repository.nextTurnSeq(920002L)).thenReturn(1);
        when(repository.insertTurn(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(repository.insertSessionMessage(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(repository.updateLastTurn(any(), any(), any())).thenReturn(1);
        when(repository.updateLastRun(any(), any(), any())).thenReturn(1);
        return repository;
    }
}
