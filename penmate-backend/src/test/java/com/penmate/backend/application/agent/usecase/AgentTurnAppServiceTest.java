package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.llm.AgentReasoningPolicy;
import com.penmate.backend.application.agent.run.*;
import com.penmate.backend.application.agent.skill.AgentSkillActivationService;
import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTurnAppServiceTest {

    private static final Long FAKE_RUN_ID = 999001L;
    private static final Long FAKE_TURN_ID = 998001L;
    private static final Long FAKE_USER_MESSAGE_ID = 997001L;

    @Test
    void should_create_turn_and_dispatch_run() {
        AgentRunAppService runAppService = runAppServiceThatSucceeds();
        AgentRunDispatcher runDispatcher = mock(AgentRunDispatcher.class);
        AgentTurnAppService agentTurnAppService = new AgentTurnAppService(
                mock(SessionStyleBindingAppService.class),
                agentRepository(),
                sessionRepository(),
                businessIdGenerator(FAKE_USER_MESSAGE_ID, FAKE_TURN_ID, FAKE_RUN_ID),
                runAppService,
                runDispatcher,
                mock(AgentSkillActivationService.class),
                recoveryPrompts(),
                mock(com.penmate.backend.application.agent.safety.AgentSafetyModeApplicationService.class)
        );

        Long projectId = 920001L;
        Long sessionId = AgentSession.active(920002L, projectId, 1001L, "Session-A").getSessionId();
        AgentTurnCommand command = new AgentTurnCommand(
                1001L,
                "write a chapter",
                java.util.List.of(),
                new AgentTurnCommand.TaskRequest(3001L, java.util.List.of(3001L), null, "selected text")
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
        AgentRunDispatcher runDispatcher = mock(AgentRunDispatcher.class);
        AgentTurnAppService agentTurnAppService = new AgentTurnAppService(
                mock(SessionStyleBindingAppService.class),
                agentRepository(),
                sessionRepository(),
                businessIdGenerator(FAKE_USER_MESSAGE_ID, FAKE_TURN_ID, FAKE_RUN_ID),
                runAppService,
                runDispatcher,
                mock(AgentSkillActivationService.class),
                recoveryPrompts(),
                mock(com.penmate.backend.application.agent.safety.AgentSafetyModeApplicationService.class)
        );

        AgentTurnResult result = agentTurnAppService.createTurn(
                920001L,
                920002L,
                new AgentTurnCommand(1001L, "hello", java.util.List.of(),
                        new AgentTurnCommand.TaskRequest(null, java.util.List.of(), null, null)),
                "trace-2"
        );

        assertThat(result.activeRun().runId()).isNotNull();
        assertThat(result.activeRun().turnId()).isEqualTo(FAKE_TURN_ID);
        assertThat(result.activeRun().runStatus()).isEqualTo("running");
    }

    @Test
    void should_use_recovery_augmented_prompt_only_for_the_created_run_input() {
        AgentRunAppService runAppService = runAppServiceThatSucceeds();
        AgentRunRecoveryPromptService recovery = mock(AgentRunRecoveryPromptService.class);
        when(recovery.attachToManualRequest(920001L, 920002L, "continue"))
                .thenReturn("[Application recovery record]\n[Current user request]\ncontinue");
        AgentTurnAppService service = new AgentTurnAppService(
                mock(SessionStyleBindingAppService.class),
                agentRepository(),
                sessionRepository(),
                businessIdGenerator(FAKE_USER_MESSAGE_ID, FAKE_TURN_ID, FAKE_RUN_ID),
                runAppService,
                mock(AgentRunDispatcher.class),
                mock(AgentSkillActivationService.class),
                recovery,
                mock(com.penmate.backend.application.agent.safety.AgentSafetyModeApplicationService.class)
        );

        service.createTurn(920001L, 920002L,
                new AgentTurnCommand(1001L, "continue", java.util.List.of(),
                        new AgentTurnCommand.TaskRequest(null, java.util.List.of(), null, null)),
                "trace-recovery");

        verify(runAppService).createRun(argThat(command ->
                command.promptSnapshot().startsWith("[Application recovery record]")
                        && command.promptSnapshot().endsWith("continue")));
    }

    @Test
    void should_freeze_resolved_reasoning_configuration_in_run_input() {
        AgentRunAppService runAppService = runAppServiceThatSucceeds();
        AgentModelRoutingService modelRouting = mock(AgentModelRoutingService.class);
        when(modelRouting.resolveSnapshot(1001L, 4001L)).thenReturn(
                new AgentModelRoutingService.ModelExecutionSnapshot(
                        4001L, new AgentReasoningPolicy("high", "detailed", "pro")));
        AgentTurnAppService service = new AgentTurnAppService(
                mock(SessionStyleBindingAppService.class),
                agentRepository(),
                sessionRepository(),
                businessIdGenerator(FAKE_USER_MESSAGE_ID, FAKE_TURN_ID, FAKE_RUN_ID),
                runAppService,
                mock(AgentRunDispatcher.class),
                mock(AgentSkillActivationService.class),
                recoveryPrompts(),
                mock(com.penmate.backend.application.agent.safety.AgentSafetyModeApplicationService.class),
                modelRouting
        );

        service.createTurn(920001L, 920002L,
                new AgentTurnCommand(1001L, "plan the revision", java.util.List.of(),
                        new AgentTurnCommand.TaskRequest(null, java.util.List.of(), 4001L, null)),
                "trace-reasoning-snapshot");

        verify(runAppService).createRun(argThat(command -> {
            String snapshot = command.modelSnapshotJson();
            return snapshot.contains("\"modelConfigId\":4001")
                    && snapshot.contains("\"reasoningEffort\":\"high\"")
                    && snapshot.contains("\"reasoningMode\":\"pro\"")
                    && snapshot.contains("\"reasoningSummary\":\"detailed\"");
        }));
    }

    private static AgentRunAppService runAppServiceThatSucceeds() {
        AgentRunAppService service = mock(AgentRunAppService.class);
        when(service.createRun(any())).thenReturn(new AgentRunResult(FAKE_RUN_ID, "running", "created", 1L));
        return service;
    }

    private static AgentRunRecoveryPromptService recoveryPrompts() {
        AgentRunRecoveryPromptService service = mock(AgentRunRecoveryPromptService.class);
        when(service.attachToManualRequest(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
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
        when(repository.findSession(920001L, 920002L))
                .thenReturn(AgentSession.active(920002L, 920001L, 1001L, "Session-A"));
        return repository;
    }
}
