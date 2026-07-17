package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.context.AgentRunContextArtifactService;
import com.penmate.backend.application.agent.context.AgentRunContextResolutionService;
import com.penmate.backend.application.agent.context.AgentRunDependencyValidator;
import com.penmate.backend.application.agent.prompt.PromptComposer;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgentRunWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(AgentRunRepository.class, () -> mock(AgentRunRepository.class))
            .withBean(AgentSessionRepository.class, () -> mock(AgentSessionRepository.class))
            .withBean(BusinessIdGenerator.class, () -> mock(BusinessIdGenerator.class))
            .withBean(AgentRunEventPublisher.class, () -> mock(AgentRunEventPublisher.class))
            .withBean(AgentRunContextResolutionService.class, () -> mock(AgentRunContextResolutionService.class))
            .withBean(PromptComposer.class, () -> mock(PromptComposer.class))
            .withBean(AgentRunLlmLoop.class, () -> mock(AgentRunLlmLoop.class))
            .withBean(AgentModelRoutingService.class, () -> mock(AgentModelRoutingService.class))
            .withBean(AgentRuntimeStateReducer.class, () -> mock(AgentRuntimeStateReducer.class))
            .withBean(AgentCheckpointService.class, () -> mock(AgentCheckpointService.class))
            .withBean(AgentRunPendingApprovalRepository.class, () -> mock(AgentRunPendingApprovalRepository.class))
            .withBean(AgentRunContextArtifactService.class, () -> mock(AgentRunContextArtifactService.class))
            .withBean(AgentRunRecoveryService.class, () -> mock(AgentRunRecoveryService.class))
            .withBean(AgentRunLeaseService.class, () -> mock(AgentRunLeaseService.class))
            .withBean(AgentRunDependencyValidator.class, () -> mock(AgentRunDependencyValidator.class))
            .withBean(AgentRunContinuationArtifactService.class,
                    () -> mock(AgentRunContinuationArtifactService.class))
            .withBean(AgentRunSuccessorService.class)
            .withBean(AgentRunExecutor.class)
            .withBean(AsyncAgentRunDispatcher.class)
            .withBean(AgentRunDispatchRequestedListener.class);

    @Test
    void wires_dispatcher_executor_and_successor_without_a_dependency_cycle() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AgentRunSuccessorService.class);
            assertThat(context).hasSingleBean(AgentRunExecutor.class);
            assertThat(context).hasSingleBean(AgentRunDispatcher.class);
        });
    }
}
