package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.agent.context.AgentRunContextArtifactService;
import com.penmate.backend.application.agent.context.AgentRunContextResolutionService;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;
import com.penmate.backend.application.agent.prompt.PromptComposer;
import com.penmate.backend.application.agent.prompt.PromptPlan;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import com.penmate.backend.domain.agent.run.model.AgentRunLease;
import com.penmate.backend.domain.agent.run.model.AgentRuntimeState;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class AgentRunExecutor {

    private final AgentRunRepository runRepository;
    private final AgentRunEventPublisher eventPublisher;
    private final AgentRunContextResolutionService contextResolutionService;
    private final PromptComposer promptComposer;
    private final AgentRunLlmLoop llmLoop;
    private final AgentModelRoutingService modelRoutingService;
    private final AgentRuntimeStateReducer stateReducer;
    private final AgentCheckpointService checkpointService;
    private final AgentRunPendingApprovalRepository pendingApprovals;
    private final AgentRunContextArtifactService contextArtifacts;
    private final AgentRunRecoveryService recoveryService;
    private final AgentRunLeaseService leaseService;

    public AgentRunExecutor(AgentRunRepository runRepository,
                            AgentRunEventPublisher eventPublisher,
                            AgentRunContextResolutionService contextResolutionService,
                            PromptComposer promptComposer,
                            AgentRunLlmLoop llmLoop,
                            AgentModelRoutingService modelRoutingService,
                            AgentRuntimeStateReducer stateReducer,
                            AgentCheckpointService checkpointService,
                            AgentRunPendingApprovalRepository pendingApprovals,
                            AgentRunContextArtifactService contextArtifacts,
                            AgentRunRecoveryService recoveryService,
                            AgentRunLeaseService leaseService) {
        this.runRepository = runRepository;
        this.eventPublisher = eventPublisher;
        this.contextResolutionService = contextResolutionService;
        this.promptComposer = promptComposer;
        this.llmLoop = llmLoop;
        this.modelRoutingService = modelRoutingService;
        this.stateReducer = stateReducer;
        this.checkpointService = checkpointService;
        this.pendingApprovals = pendingApprovals;
        this.contextArtifacts = contextArtifacts;
        this.recoveryService = recoveryService;
        this.leaseService = leaseService;
    }

    public void execute(Long runId, String traceId) {
        execute(runId, traceId, null);
    }

    public void execute(Long runId, String traceId, AgentRunLease lease) {
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

        AgentEvent evt = eventPublisher.publish(runId, "run.phase.changed", Map.of("phase", "routing"));
        state = stateReducer.apply(state, evt);
        checkpointService.checkpointIfNeeded(evt, state);

        TaskProfile taskProfile = TaskProfile.fromTaskType(input.taskType());

        evt = eventPublisher.publish(runId, "run.phase.changed", Map.of("phase", "epoch_binding"));
        state = stateReducer.apply(state, evt);
        checkpointService.checkpointIfNeeded(evt, state);

        AgentRunContextResolutionService.Resolution contextResult = contextResolutionService.resolveInitial(
                run, input, taskProfile, executionConfig, traceId);
        evt = eventPublisher.publish(runId, "context.epoch.bound", Map.of(
                "contextEpochId", contextResult.epochBinding().epoch().epochId(),
                "reused", contextResult.epochBinding().reused()
        ));
        state = stateReducer.apply(state, evt);
        checkpointService.checkpointIfNeeded(evt, state);
        evt = eventPublisher.publish(runId, "turn.route.completed", Map.of(
                "mode", contextResult.routeDecision().mode().name(),
                "selectedNodeIds", contextResult.routeDecision().selectedNodeIds(),
                "selectorUsed", contextResult.routeDecision().selectorUsed(),
                "selectorLatencyMillis", contextResult.routeDecision().selectorLatencyMillis(),
                "semanticUnavailable", contextResult.routeDecision().semanticUnavailable()
        ));
        state = stateReducer.apply(state, evt);
        checkpointService.checkpointIfNeeded(evt, state);
        evt = eventPublisher.publish(runId, "context.resolved", Map.of(
                "artifactId", contextResult.artifactRef().artifactId(),
                "sha256", contextResult.artifactRef().sha256(),
                "sizeBytes", contextResult.artifactRef().sizeBytes()
        ));
        state = stateReducer.apply(state, evt);
        checkpointService.checkpointIfNeeded(evt, state);
        contextResolutionService.promoteAfterDurable(sessionId, turnId, contextResult.routeDecision().selectedNodeIds());

        evt = eventPublisher.publish(runId, "run.phase.changed", Map.of("phase", "prompt"));
        state = stateReducer.apply(state, evt);

        PromptPlan promptPlan = promptComposer.compose(taskProfile, contextResult.contextPackage(), input.promptSnapshot());
        var promptRef = contextArtifacts.savePromptPlan(runId, promptPlan,
                new AgentRunContextArtifactService.PromptManifest(
                        contextResult.epochBinding().epoch().epochId(),
                        contextResult.catalogHashes().promptBundleHash(),
                        contextResult.catalogHashes().toolCatalogHash(),
                        contextResult.catalogHashes().skillCatalogHash(),
                        contextResult.storyBibleCoreHash(),
                        sha256(promptPlan.stablePrefix()), sha256(promptPlan.dynamicContext())
                ));
        evt = eventPublisher.publish(runId, "prompt.composed", Map.of(
                "artifactId", promptRef.artifactId(), "sha256", promptRef.sha256(), "sizeBytes", promptRef.sizeBytes()
        ));
        state = stateReducer.apply(state, evt);
        checkpointService.checkpointIfNeeded(evt, state);

        evt = eventPublisher.publish(runId, "run.phase.changed", Map.of("phase", "executing"));
        state = stateReducer.apply(state, evt);

        AgentRunLoopResult loopResult = llmLoop.execute(new AgentRunLoopRequest(
                runId,
                projectId,
                sessionId,
                turnId,
                traceId,
                promptMessages(promptPlan, input.promptSnapshot()),
                executionConfig,
                userId
        ));

        if (loopResult.status() == AgentRunLoopResult.Status.WAITING_APPROVAL) {
            evt = eventPublisher.publish(runId, "run.waiting_approval", Map.of("approvalId", loopResult.approvalId()));
            state = stateReducer.apply(state, evt);
            checkpointService.checkpointIfNeeded(evt, state);
            if (lease != null) leaseService.waitingApproval(lease, loopResult.approvalId());
            return;
        }
        if (loopResult.status() == AgentRunLoopResult.Status.FAILED) {
            evt = eventPublisher.publish(runId, "run.failed", Map.of("phase", "failed", "message", loopResult.finalAssistantText()));
            state = stateReducer.apply(state, evt);
            checkpointService.checkpointIfNeeded(evt, state);
            if (lease != null) leaseService.failTerminal(lease, "AGENT_RUN_FAILED", loopResult.finalAssistantText());
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
        if (lease != null) leaseService.complete(lease);
    }

    public void resume(Long runId, String traceId) {
        resume(runId, traceId, null);
    }

    private void resume(Long runId, String traceId, AgentRunLease lease) {
        AgentRun run = runRepository.findRun(runId);
        AgentRunInput input = runRepository.findInput(runId);
        if (run == null || input == null) throw new IllegalArgumentException("Agent run not found: " + runId);
        AgentRuntimeState state = recoveryService.recover(runId);
        if (state == null || state.artifactRefs().isEmpty()) {
            throw new IllegalStateException("Run has no recoverable resolved-context checkpoint: " + runId);
        }
        var contextArtifact = contextArtifacts.loadContextForRun(runId, state.artifactRefs());
        if (run.contextEpochId() == null || !run.contextEpochId().equals(contextArtifact.contextEpochId())) {
            throw new IllegalStateException("Run Context Epoch does not match its immutable artifact");
        }
        var pending = pendingApprovals.findApprovedByRunId(runId);
        if (pending == null || !"APPROVED".equals(pending.pendingStatus())) {
            throw new IllegalStateException("Run approval is not ready for resume");
        }
        Long modelConfigId = extractModelConfigIdFromSnapshot(input.modelSnapshotJson());
        AgentLlmExecutionConfig executionConfig = modelConfigId == null
                ? AgentLlmExecutionConfig.builder().build()
                : modelRoutingService.resolveExecutionConfig(run.ownerUserId(), modelConfigId, traceId);
        AgentRunLoopResult result = llmLoop.resumeApproved(new AgentRunLoopRequest(
                runId, run.projectId(), run.sessionId(), run.turnId(), traceId, List.of(), executionConfig,
                run.ownerUserId()), pending);
        pendingApprovals.markStatus(pending.approvalId(), "APPROVED", "COMPLETED");
        if (result.status() == AgentRunLoopResult.Status.WAITING_APPROVAL) {
            AgentEvent waiting = eventPublisher.publish(runId, "run.waiting_approval", Map.of("approvalId", result.approvalId()));
            checkpointService.checkpointIfNeeded(waiting, stateReducer.apply(state, waiting));
            if (lease != null) leaseService.waitingApproval(lease, result.approvalId());
            return;
        }
        if (result.status() == AgentRunLoopResult.Status.FAILED) {
            AgentEvent failed = eventPublisher.publish(runId, "run.failed", Map.of("phase", "failed", "message", result.finalAssistantText()));
            checkpointService.checkpointIfNeeded(failed, stateReducer.apply(state, failed));
            if (lease != null) leaseService.failTerminal(lease, "AGENT_RUN_FAILED", result.finalAssistantText());
            return;
        }
        AgentEvent message = eventPublisher.publish(runId, "message.completed", Map.of(
                "role", "assistant", "text", result.finalAssistantText()));
        state = stateReducer.apply(state, message);
        AgentEvent completed = eventPublisher.publish(runId, "run.completed", Map.of(
                "phase", "completed", "tokenUsage", result.tokenUsage()));
        checkpointService.checkpointIfNeeded(completed, stateReducer.apply(state, completed));
        if (lease != null) leaseService.complete(lease);
    }

    public void recover(Long runId, String traceId, AgentRunLease lease) {
        if (pendingApprovals.findApprovedByRunId(runId) != null) {
            resume(runId, traceId, lease);
            return;
        }

        AgentRun run = runRepository.findRun(runId);
        AgentRunInput input = runRepository.findInput(runId);
        if (run == null || input == null) throw new IllegalArgumentException("Agent run not found: " + runId);
        AgentRuntimeState state = recoveryService.recover(runId);
        if (state == null || state.artifactRefs().isEmpty()) {
            execute(runId, traceId, lease);
            return;
        }

        var contextArtifact = contextArtifacts.loadContextForRun(runId, state.artifactRefs());
        if (run.contextEpochId() == null || !run.contextEpochId().equals(contextArtifact.contextEpochId())) {
            throw new IllegalStateException("Run Context Epoch does not match its immutable artifact");
        }
        var promptArtifact = contextArtifacts.loadPromptPlanForRun(runId, state.artifactRefs());
        Long modelConfigId = extractModelConfigIdFromSnapshot(input.modelSnapshotJson());
        AgentLlmExecutionConfig executionConfig = modelConfigId == null
                ? AgentLlmExecutionConfig.builder().build()
                : modelRoutingService.resolveExecutionConfig(run.ownerUserId(), modelConfigId, traceId);
        leaseService.assertOwned(lease);
        AgentRunLoopResult result = llmLoop.execute(new AgentRunLoopRequest(
                runId, run.projectId(), run.sessionId(), run.turnId(), traceId,
                promptMessages(promptArtifact.plan(), input.promptSnapshot()), executionConfig, run.ownerUserId()));
        finishRecoveredRun(runId, result, state, lease);
    }

    private void finishRecoveredRun(Long runId, AgentRunLoopResult result,
                                    AgentRuntimeState state, AgentRunLease lease) {
        if (result.status() == AgentRunLoopResult.Status.WAITING_APPROVAL) {
            AgentEvent waiting = eventPublisher.publish(runId, "run.waiting_approval", Map.of("approvalId", result.approvalId()));
            checkpointService.checkpointIfNeeded(waiting, stateReducer.apply(state, waiting));
            leaseService.waitingApproval(lease, result.approvalId());
            return;
        }
        if (result.status() == AgentRunLoopResult.Status.FAILED) {
            AgentEvent failed = eventPublisher.publish(runId, "run.failed", Map.of(
                    "phase", "failed", "message", result.finalAssistantText()));
            checkpointService.checkpointIfNeeded(failed, stateReducer.apply(state, failed));
            leaseService.failTerminal(lease, "AGENT_RUN_FAILED", result.finalAssistantText());
            return;
        }
        AgentEvent message = eventPublisher.publish(runId, "message.completed", Map.of(
                "role", "assistant", "text", result.finalAssistantText()));
        state = stateReducer.apply(state, message);
        AgentEvent completed = eventPublisher.publish(runId, "run.completed", Map.of(
                "phase", "completed", "tokenUsage", result.tokenUsage()));
        checkpointService.checkpointIfNeeded(completed, stateReducer.apply(state, completed));
        leaseService.complete(lease);
    }

    private List<AgentLlmMessage> promptMessages(PromptPlan plan, String userRequest) {
        List<AgentLlmMessage> messages = new java.util.ArrayList<>();
        messages.add(AgentLlmMessage.system(plan.stablePrefix()));
        if (!plan.dynamicContext().isBlank()) messages.add(AgentLlmMessage.system(plan.dynamicContext()));
        messages.add(AgentLlmMessage.user(userRequest));
        return List.copyOf(messages);
    }

    private String sha256(String value) {
        try {
            byte[] bytes = (value == null ? "" : value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
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
