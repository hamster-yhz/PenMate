package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.agent.context.AgentContextRoutingFacade;
import com.penmate.backend.application.agent.context.AgentContextRoutingRequest;
import com.penmate.backend.application.agent.context.AgentContextRoutingResult;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightCoordinator;
import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightDecision;
import com.penmate.backend.application.agent.orchestration.preflight.AgentPreflightRequest;
import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;
import com.penmate.backend.application.agent.prompt.PromptComposer;
import com.penmate.backend.application.agent.prompt.PromptPlan;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class AgentRunExecutor {

    private final AgentRunRepository runRepository;
    private final AgentRunEventPublisher eventPublisher;
    private final AgentPreflightCoordinator preflightCoordinator;
    private final AgentContextRoutingFacade contextRoutingFacade;
    private final PromptComposer promptComposer;
    private final AgentRunLlmLoop llmLoop;

    public AgentRunExecutor(AgentRunRepository runRepository,
                            AgentRunEventPublisher eventPublisher,
                            AgentPreflightCoordinator preflightCoordinator,
                            AgentContextRoutingFacade contextRoutingFacade,
                            PromptComposer promptComposer,
                            AgentRunLlmLoop llmLoop) {
        this.runRepository = runRepository;
        this.eventPublisher = eventPublisher;
        this.preflightCoordinator = preflightCoordinator;
        this.contextRoutingFacade = contextRoutingFacade;
        this.promptComposer = promptComposer;
        this.llmLoop = llmLoop;
    }

    public void execute(Long runId, String traceId) {
        Objects.requireNonNull(runId, "runId must not be null");
        AgentRunInput input = runRepository.findInput(runId);
        if (input == null) {
            throw new IllegalArgumentException("Agent run input not found: " + runId);
        }

        Long projectId = runId;
        Long sessionId = runId;
        Long turnId = runId;
        AgentLlmExecutionConfig executionConfig = AgentLlmExecutionConfig.builder().build();

        publishPhase(runId, "preflight");
        AgentPreflightDecision decision = preflightCoordinator.coordinate(new AgentPreflightRequest(
                projectId,
                sessionId,
                input.chapterId(),
                input.promptSnapshot(),
                executionConfig
        ));
        TaskProfile taskProfile = taskProfileFrom(decision);

        publishPhase(runId, "context");
        AgentContextRoutingResult contextResult = contextRoutingFacade.route(new AgentContextRoutingRequest(
                projectId,
                sessionId,
                input.chapterId(),
                input.promptSnapshot(),
                input.styleSnapshotJson(),
                decision,
                taskProfile
        ));
        eventPublisher.publish(runId, "context.routing.completed", Map.of(
                "sourceCount", contextResult.contextPackage().sources().size(),
                "ragRefCount", contextResult.contextPackage().ragRefs().size()
        ));

        publishPhase(runId, "prompt");
        PromptPlan promptPlan = promptComposer.compose(taskProfile, contextResult.contextPackage(), input.promptSnapshot());

        publishPhase(runId, "executing");
        AgentRunLoopResult loopResult = llmLoop.execute(new AgentRunLoopRequest(
                runId,
                projectId,
                sessionId,
                turnId,
                traceId,
                List.of(
                        AgentLlmMessage.system(promptPlan.assembledPromptPreview()),
                        AgentLlmMessage.user(input.promptSnapshot())
                ),
                executionConfig
        ));

        if (loopResult.status() == AgentRunLoopResult.Status.WAITING_APPROVAL) {
            eventPublisher.publish(runId, "run.waiting_approval", Map.of("approvalId", loopResult.approvalId()));
            return;
        }

        eventPublisher.publish(runId, "message.completed", Map.of(
                "role", "assistant",
                "text", loopResult.finalAssistantText()
        ));
        eventPublisher.publish(runId, "run.completed", Map.of(
                "phase", "completed",
                "tokenUsage", loopResult.tokenUsage()
        ));
    }

    public void resume(Long runId, String traceId) {
        execute(runId, traceId);
    }

    private void publishPhase(Long runId, String phase) {
        eventPublisher.publish(runId, "run.phase.changed", Map.of("phase", phase));
    }

    private TaskProfile taskProfileFrom(AgentPreflightDecision decision) {
        return new TaskProfile(
                List.of(),
                decision.executionPromptProfile(),
                decision.enabledSkills(),
                decision.enabledTools(),
                decision.hardConstraints(),
                decision.outputExpectation(),
                decision.needsApproval(),
                decision.includeStoryBibleContext() || decision.needsStoryBibleUpdate(),
                decision.includeRagContext(),
                decision.reasoningSummary()
        );
    }
}
