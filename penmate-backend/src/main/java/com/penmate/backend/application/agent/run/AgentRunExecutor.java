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
import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import com.penmate.backend.domain.agent.run.model.AgentRuntimeState;
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
    private final AgentModelRoutingService modelRoutingService;
    private final AgentRuntimeStateReducer stateReducer;
    private final AgentCheckpointService checkpointService;

    public AgentRunExecutor(AgentRunRepository runRepository,
                            AgentRunEventPublisher eventPublisher,
                            AgentPreflightCoordinator preflightCoordinator,
                            AgentContextRoutingFacade contextRoutingFacade,
                            PromptComposer promptComposer,
                            AgentRunLlmLoop llmLoop,
                            AgentModelRoutingService modelRoutingService,
                            AgentRuntimeStateReducer stateReducer,
                            AgentCheckpointService checkpointService) {
        this.runRepository = runRepository;
        this.eventPublisher = eventPublisher;
        this.preflightCoordinator = preflightCoordinator;
        this.contextRoutingFacade = contextRoutingFacade;
        this.promptComposer = promptComposer;
        this.llmLoop = llmLoop;
        this.modelRoutingService = modelRoutingService;
        this.stateReducer = stateReducer;
        this.checkpointService = checkpointService;
    }

    public void execute(Long runId, String traceId) {
        Objects.requireNonNull(runId, "runId must not be null");
        AgentRunInput input = runRepository.findInput(runId);
        if (input == null) {
            throw new IllegalArgumentException("Agent run input not found: " + runId);
        }

        AgentRun run = runRepository.findRun(runId);
        if (run == null) {
            throw new IllegalStateException("Agent run not found: " + runId);
        }
        Long userId = run.ownerUserId();
        Long modelConfigId = extractModelConfigIdFromSnapshot(input.modelSnapshotJson());
        AgentLlmExecutionConfig executionConfig;
        if (modelConfigId != null) {
            executionConfig = modelRoutingService.resolveExecutionConfig(userId, modelConfigId, traceId);
        } else {
            executionConfig = AgentLlmExecutionConfig.builder().build();
        }
        Long projectId = run.projectId();
        Long sessionId = run.sessionId();
        Long turnId = run.turnId();

        AgentRuntimeState state = AgentRuntimeState.empty(runId);

        AgentEvent evt = eventPublisher.publish(runId, "run.phase.changed", Map.of("phase", "preflight"));
        state = stateReducer.apply(state, evt);
        checkpointService.checkpointIfNeeded(evt, state);

        AgentPreflightDecision decision = preflightCoordinator.coordinate(new AgentPreflightRequest(
                projectId,
                sessionId,
                input.chapterId(),
                input.promptSnapshot(),
                executionConfig
        ));
        TaskProfile taskProfile = taskProfileFrom(decision);

        evt = eventPublisher.publish(runId, "run.phase.changed", Map.of("phase", "context"));
        state = stateReducer.apply(state, evt);
        checkpointService.checkpointIfNeeded(evt, state);

        AgentContextRoutingResult contextResult = contextRoutingFacade.route(new AgentContextRoutingRequest(
                projectId,
                sessionId,
                input.chapterId(),
                input.promptSnapshot(),
                input.styleSnapshotJson(),
                decision,
                taskProfile
        ));
        evt = eventPublisher.publish(runId, "context.routing.completed", Map.of(
                "sourceCount", contextResult.contextPackage().sources().size(),
                "ragRefCount", contextResult.contextPackage().ragRefs().size()
        ));
        state = stateReducer.apply(state, evt);
        checkpointService.checkpointIfNeeded(evt, state);

        evt = eventPublisher.publish(runId, "run.phase.changed", Map.of("phase", "prompt"));
        state = stateReducer.apply(state, evt);

        PromptPlan promptPlan = promptComposer.compose(taskProfile, contextResult.contextPackage(), input.promptSnapshot());

        evt = eventPublisher.publish(runId, "run.phase.changed", Map.of("phase", "executing"));
        state = stateReducer.apply(state, evt);

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
            evt = eventPublisher.publish(runId, "run.waiting_approval", Map.of("approvalId", loopResult.approvalId()));
            state = stateReducer.apply(state, evt);
            checkpointService.checkpointIfNeeded(evt, state);
            return;
        }

        evt = eventPublisher.publish(runId, "message.completed", Map.of(
                "role", "assistant",
                "text", loopResult.finalAssistantText()
        ));
        state = stateReducer.apply(state, evt);

        evt = eventPublisher.publish(runId, "run.completed", Map.of(
                "phase", "completed",
                "tokenUsage", loopResult.tokenUsage()
        ));
        state = stateReducer.apply(state, evt);
        checkpointService.checkpointIfNeeded(evt, state);
    }

    public void resume(Long runId, String traceId) {
        execute(runId, traceId);
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

    private Long extractModelConfigIdFromSnapshot(String modelSnapshotJson) {
        if (modelSnapshotJson == null || modelSnapshotJson.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(modelSnapshotJson);
            com.fasterxml.jackson.databind.JsonNode modelConfigId = node.get("modelConfigId");
            if (modelConfigId != null && !modelConfigId.isNull()) {
                long value = modelConfigId.asLong(0L);
                return value > 0 ? value : null;
            }
        } catch (Exception e) {
            // ignore parse failures
        }
        return null;
    }
}
