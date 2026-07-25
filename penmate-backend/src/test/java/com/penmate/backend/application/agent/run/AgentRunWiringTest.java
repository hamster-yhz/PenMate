package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.context.AgentRunContextArtifactService;
import com.penmate.backend.application.agent.context.AgentRunContextResolutionService;
import com.penmate.backend.application.agent.context.AgentRunDependencyValidator;
import com.penmate.backend.application.agent.orchestration.AgentPromptAssembler;
import com.penmate.backend.application.agent.prompt.PromptComposer;
import com.penmate.backend.application.agent.skill.AgentSkillActivationService;
import com.penmate.backend.application.agent.tool.selection.AgentToolSelectionPolicy;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.infrastructure.agent.run.AgentRunDispatchRequestedListener;
import com.penmate.backend.infrastructure.agent.run.AsyncAgentRunDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgentRunWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(AgentRunRepository.class, () -> mock(AgentRunRepository.class))
            .withBean(AgentSessionRepository.class, () -> mock(AgentSessionRepository.class))
            .withBean(BusinessIdGenerator.class, () -> mock(BusinessIdGenerator.class))
            .withBean(JsonCodec.class, () -> mock(JsonCodec.class))
            .withBean(AgentRunEventPublisher.class, () -> mock(AgentRunEventPublisher.class))
            .withBean(AgentRunContextResolutionService.class, () -> mock(AgentRunContextResolutionService.class))
            .withBean(PromptComposer.class, () -> mock(PromptComposer.class))
            .withBean(AgentPromptAssembler.class, () -> mock(AgentPromptAssembler.class))
            .withBean(AgentRunLlmLoop.class, () -> mock(AgentRunLlmLoop.class))
            .withBean(AgentModelRoutingService.class, () -> mock(AgentModelRoutingService.class))
            .withBean(AgentRuntimeStateReducer.class, () -> mock(AgentRuntimeStateReducer.class))
            .withBean(AgentCheckpointService.class, () -> mock(AgentCheckpointService.class))
            .withBean(AgentRunPendingApprovalRepository.class, () -> mock(AgentRunPendingApprovalRepository.class))
            .withBean(AgentRunContextArtifactService.class, () -> mock(AgentRunContextArtifactService.class))
            .withBean(AgentRunRecoveryService.class, () -> mock(AgentRunRecoveryService.class))
            .withBean(AgentRunLeaseService.class, () -> mock(AgentRunLeaseService.class))
            .withBean(AgentRunOutputEventService.class, () -> mock(AgentRunOutputEventService.class))
            .withBean(AgentRunDependencyValidator.class, () -> mock(AgentRunDependencyValidator.class))
            .withBean(AgentToolSelectionPolicy.class, () -> mock(AgentToolSelectionPolicy.class))
            .withBean(AgentSkillActivationService.class, () -> mock(AgentSkillActivationService.class))
            .withBean(AgentRunContinuationArtifactService.class,
                    () -> mock(AgentRunContinuationArtifactService.class))
            .withBean(AgentRunDispatchRequestPublisher.class,
                    () -> mock(AgentRunDispatchRequestPublisher.class))
            .withBean(AgentRunSuccessorService.class)
            .withBean(AgentRunStateTransitionService.class)
            .withBean(AgentRunExecutor.class)
            .withBean(AgentRunInitialExecutionService.class)
            .withBean(AsyncAgentRunDispatcher.class)
            .withBean(AgentRunDispatchRequestedListener.class);

    @Test
    void wires_dispatcher_executor_and_successor_without_a_dependency_cycle() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AgentRunSuccessorService.class);
            assertThat(context).hasSingleBean(AgentRunStateTransitionService.class);
            assertThat(context).hasSingleBean(AgentRunExecutor.class);
            assertThat(context).hasSingleBean(AgentRunDispatcher.class);
        });
    }
}
