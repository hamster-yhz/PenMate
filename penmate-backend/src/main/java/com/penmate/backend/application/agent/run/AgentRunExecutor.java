package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.agent.context.AgentRunContextArtifactService;
import com.penmate.backend.application.agent.context.AgentRunContextResolutionService;
import com.penmate.backend.application.agent.context.AgentRunDependencyValidator;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.orchestration.AgentPromptAssembler;
import com.penmate.backend.application.agent.prompt.PromptComposer;
import com.penmate.backend.application.agent.prompt.PromptPlan;
import com.penmate.backend.application.agent.skill.AgentSkillActivationService;
import com.penmate.backend.application.agent.tool.selection.AgentToolSelectionPolicy;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import com.penmate.backend.domain.agent.run.model.AgentRunLease;
import com.penmate.backend.domain.agent.run.model.AgentRuntimeState;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
public class AgentRunExecutor {

    private final AgentRunRepository runRepository;
    private final AgentRunEventPublisher eventPublisher;
    private final AgentRunContextResolutionService contextResolutionService;
    private final PromptComposer promptComposer;
    private final AgentPromptAssembler promptAssembler;
    private final AgentRunLlmLoop llmLoop;
    private final AgentModelRoutingService modelRoutingService;
    private final AgentRuntimeStateReducer stateReducer;
    private final AgentCheckpointService checkpointService;
    private final AgentRunPendingApprovalRepository pendingApprovals;
    private final AgentRunContextArtifactService contextArtifacts;
    private final AgentRunRecoveryService recoveryService;
    private final AgentRunLeaseService leaseService;
    private final AgentRunDependencyValidator dependencyValidator;
    private final AgentToolSelectionPolicy toolSelectionPolicy;
    private final AgentRunStateTransitionService stateTransitions;
    private final AgentRunContinuationArtifactService continuations;
    private final JsonCodec jsonCodec;
    private final AgentSkillActivationService skillActivationService;

    public AgentRunExecutor(AgentRunRepository runRepository,
                            AgentRunEventPublisher eventPublisher,
                            AgentRunContextResolutionService contextResolutionService,
                            PromptComposer promptComposer,
                            AgentPromptAssembler promptAssembler,
                            AgentRunLlmLoop llmLoop,
                            AgentModelRoutingService modelRoutingService,
                            AgentRuntimeStateReducer stateReducer,
                            AgentCheckpointService checkpointService,
                            AgentRunPendingApprovalRepository pendingApprovals,
                            AgentRunContextArtifactService contextArtifacts,
                            AgentRunRecoveryService recoveryService,
                            AgentRunLeaseService leaseService,
                            AgentRunDependencyValidator dependencyValidator,
                            AgentToolSelectionPolicy toolSelectionPolicy,
                            AgentRunStateTransitionService stateTransitions,
                            AgentRunContinuationArtifactService continuations,
                            JsonCodec jsonCodec,
                            AgentSkillActivationService skillActivationService) {
        this.runRepository = runRepository;
        this.eventPublisher = eventPublisher;
        this.contextResolutionService = contextResolutionService;
        this.promptComposer = promptComposer;
        this.promptAssembler = promptAssembler;
        this.llmLoop = llmLoop;
        this.modelRoutingService = modelRoutingService;
        this.stateReducer = stateReducer;
        this.checkpointService = checkpointService;
        this.pendingApprovals = pendingApprovals;
        this.contextArtifacts = contextArtifacts;
        this.recoveryService = recoveryService;
        this.leaseService = leaseService;
        this.dependencyValidator = dependencyValidator;
        this.toolSelectionPolicy = toolSelectionPolicy;
        this.stateTransitions = stateTransitions;
        this.continuations = continuations;
        this.jsonCodec = jsonCodec;
        this.skillActivationService = skillActivationService;
    }

    public void execute(Long runId, String traceId, AgentRunLease lease) {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(lease, "lease must not be null");
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

        evt = eventPublisher.publish(runId, "run.phase.changed", Map.of("phase", "epoch_binding"));
        state = stateReducer.apply(state, evt);
        checkpointService.checkpointIfNeeded(evt, state);

        AgentRunContextResolutionService.Resolution contextResult = contextResolutionService.resolveInitial(
                run, input, executionConfig, traceId);
        evt = eventPublisher.publish(runId, "context.epoch.bound", Map.of(
                "contextEpochId", contextResult.epochBinding().epoch().epochId(),
                "reused", contextResult.epochBinding().reused()
        ));
        state = stateReducer.apply(state, evt);
        checkpointService.checkpointIfNeeded(evt, state);
        var routePayload = new java.util.LinkedHashMap<String, Object>();
        routePayload.put("mode", contextResult.routeDecision().mode().name());
        routePayload.put("routingMode", contextResult.routeDecision().mode().name());
        routePayload.put("selectedNodeIds", contextResult.routeDecision().selectedNodeIds());
        routePayload.put("relationExpansionNodeIds", contextResult.routeDecision().relationExpansionNodeIds());
        routePayload.put("selectorUsed", contextResult.routeDecision().selectorUsed());
        routePayload.put("selectorLatencyMillis", contextResult.routeDecision().selectorLatencyMillis());
        routePayload.put("selectorConfidence", contextResult.routeDecision().selectorConfidence());
        routePayload.put("selectorTokenUsage", contextResult.routeDecision().selectorTokenUsage());
        routePayload.put("semanticRetrieverAvailable",
                contextResult.routeDecision().retrievalTrace().semanticRetrieverAvailable());
        routePayload.put("semanticUnavailable", contextResult.routeDecision().semanticUnavailable());
        routePayload.put("exactAliasCount", contextResult.routeDecision().retrievalTrace().exactAliasCount());
        routePayload.put("lexicalCandidateCount", contextResult.routeDecision().retrievalTrace().lexicalCandidateCount());
        routePayload.put("selectedNodeCount", contextResult.routeDecision().selectedNodeIds().size());
        evt = eventPublisher.publish(runId, "turn.route.completed", routePayload);
        state = stateReducer.apply(state, evt);
        checkpointService.checkpointIfNeeded(evt, state);
        evt = eventPublisher.publish(runId, "context.resolved", Map.of(
                "artifactId", contextResult.artifactRef().artifactId(),
                "sha256", contextResult.artifactRef().sha256(),
                "sizeBytes", contextResult.artifactRef().sizeBytes()
        ));
        state = stateReducer.apply(state, evt);
        checkpointService.checkpointIfNeeded(evt, state);
        var workingSetSummary = contextResolutionService.promoteAfterDurable(
                sessionId, turnId, contextResult.routeDecision().selectedNodeIds());
        evt = eventPublisher.publish(runId, "working_set.updated", Map.of(
                "candidateCount", workingSetSummary.candidateCount(),
                "promotedCount", workingSetSummary.promotedCount(),
                "evictedCount", workingSetSummary.evictedCount(),
                "succeeded", workingSetSummary.succeeded()
        ));
        state = stateReducer.apply(state, evt);
        checkpointService.checkpointIfNeeded(evt, state);
        log.info("agent.context.resolved: projectId={}, sessionId={}, runId={}, epochId={}, storyBibleRevision={}, "
                        + "routingMode={}, selectorUsed={}, semanticRetrieverAvailable={}, exactAliasCount={}, "
                        + "lexicalCandidateCount={}, selectedNodeCount={}, workingSetCandidateCount={}, "
                        + "workingSetPromotedCount={}, workingSetEvictedCount={}, selectorTokenUsage={}, "
                        + "selectorLatencyMillis={}, contextArtifactId={}, contextArtifactSha256={}",
                projectId, sessionId, runId, contextResult.epochBinding().epoch().epochId(),
                contextResult.epochBinding().epoch().storyBibleRevision(), contextResult.routeDecision().mode(),
                contextResult.routeDecision().selectorUsed(),
                contextResult.routeDecision().retrievalTrace().semanticRetrieverAvailable(),
                contextResult.routeDecision().retrievalTrace().exactAliasCount(),
                contextResult.routeDecision().retrievalTrace().lexicalCandidateCount(),
                contextResult.routeDecision().selectedNodeIds().size(), workingSetSummary.candidateCount(),
                workingSetSummary.promotedCount(), workingSetSummary.evictedCount(),
                contextResult.routeDecision().selectorTokenUsage(),
                contextResult.routeDecision().selectorLatencyMillis(), contextResult.artifactRef().artifactId(),
                contextResult.artifactRef().sha256());

        evt = eventPublisher.publish(runId, "run.phase.changed", Map.of("phase", "prompt"));
        state = stateReducer.apply(state, evt);

        PromptPlan promptPlan = promptComposer.compose(contextResult.contextPackage(), input.promptSnapshot());
        String activatedSkills = skillActivationService.renderExplicitSkills(runId);
        List<AgentLlmMessage> executionMessages = promptAssembler.buildExecutionMessages(
                promptPlan, activatedSkills, input.promptSnapshot(), contextResult.conversationWindow());
        var promptRef = contextArtifacts.savePromptPlan(runId, promptPlan,
                new AgentRunContextArtifactService.PromptManifest(
                        contextResult.epochBinding().epoch().epochId(),
                        contextResult.catalogHashes().promptBundleHash(),
                        contextResult.catalogHashes().toolCatalogHash(),
                        contextResult.catalogHashes().skillCatalogHash(),
                        contextResult.storyBibleCoreHash(),
                        sha256(promptPlan.stablePrefix()), sha256(promptPlan.dynamicContext())
                ), executionMessages);
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
                executionMessages,
                promptPlan.toolSchemas(),
                executionConfig,
                userId,
                lease.executionToken()
        ));

        if (loopResult.status() == AgentRunLoopResult.Status.WAITING_APPROVAL) {
            evt = stateTransitions.waitingApproval(lease, loopResult.approvalId(), null).stateEvent();
            state = stateReducer.apply(state, evt);
            checkpointService.checkpointIfNeeded(evt, state);
            return;
        }
        if (loopResult.status() == AgentRunLoopResult.Status.FAILED) {
            evt = stateTransitions.failed(lease, loopResult.finalAssistantText(), null).stateEvent();
            state = stateReducer.apply(state, evt);
            checkpointService.checkpointIfNeeded(evt, state);
            return;
        }

        var outcome = stateTransitions.completed(
                lease, loopResult.finalAssistantText(), loopResult.tokenUsage(), false, null);
        state = stateReducer.apply(state, outcome.messageEvent());
        evt = outcome.stateEvent();
        state = stateReducer.apply(state, evt);
        checkpointService.checkpointIfNeeded(evt, state);
    }

    private void resume(Long runId, String traceId, AgentRunLease lease) {
        Objects.requireNonNull(lease, "lease must not be null");
        AgentRun run = runRepository.findRun(runId);
        AgentRunInput input = runRepository.findInput(runId);
        if (run == null || input == null) throw new IllegalArgumentException("Agent run not found: " + runId);
        AgentRuntimeState state = recoveryService.recover(runId);
        if (state != null && "DONE".equals(state.status())) {
            leaseService.complete(lease);
            return;
        }
        if (state != null && "FAILED".equals(state.status())) {
            leaseService.failTerminal(lease, "AGENT_RUN_RECOVERED_FAILED_EVENT",
                    "Run failure event was durable before the previous worker stopped");
            return;
        }
        if (state == null || state.artifactRefs().isEmpty()) {
            throw new IllegalStateException("Run has no recoverable resolved-context checkpoint: " + runId);
        }
        var contextArtifact = contextArtifacts.loadContextForRun(runId, state.artifactRefs());
        if (run.contextEpochId() == null || !run.contextEpochId().equals(contextArtifact.contextEpochId())) {
            throw new IllegalStateException("Run Context Epoch does not match its immutable artifact");
        }
        if (lease != null && !continueIfDependenciesCurrent(run, input, contextArtifact, lease, traceId)) return;
        var promptArtifact = contextArtifacts.loadPromptPlanForRun(runId, state.artifactRefs());
        var pending = pendingApprovals.findApprovedByRunId(runId);
        if (pending == null) pending = pendingApprovals.findRejectedByRunId(runId);
        if (pending == null || !("APPROVED".equals(pending.pendingStatus())
                || "REJECTED".equals(pending.pendingStatus()))) {
            throw new IllegalStateException("Run approval is not ready for resume");
        }
        Long modelConfigId = extractModelConfigIdFromSnapshot(input.modelSnapshotJson());
        AgentLlmExecutionConfig executionConfig = modelConfigId == null
                ? AgentLlmExecutionConfig.builder().build()
                : modelRoutingService.resolveExecutionConfig(run.ownerUserId(), modelConfigId, traceId);
        AgentRunLoopRequest loopRequest = new AgentRunLoopRequest(
                runId, run.projectId(), run.sessionId(), run.turnId(), traceId, List.of(),
                resolveToolSchemas(promptArtifact), executionConfig,
                run.ownerUserId(), lease.executionToken());
        AgentRunLoopResult result = "APPROVED".equals(pending.pendingStatus())
                ? llmLoop.resumeApproved(loopRequest, pending)
                : llmLoop.resumeRejected(loopRequest, pending);
        if (result.status() == AgentRunLoopResult.Status.WAITING_APPROVAL) {
            AgentEvent waiting = stateTransitions.waitingApproval(
                    lease, result.approvalId(), pending.approvalId()).stateEvent();
            checkpointService.checkpointIfNeeded(waiting, stateReducer.apply(state, waiting));
            return;
        }
        if (result.status() == AgentRunLoopResult.Status.FAILED) {
            AgentEvent failed = stateTransitions.failed(
                    lease, result.finalAssistantText(), pending.approvalId()).stateEvent();
            checkpointService.checkpointIfNeeded(failed, stateReducer.apply(state, failed));
            return;
        }
        var outcome = stateTransitions.completed(lease, result.finalAssistantText(), result.tokenUsage(),
                state.assistantMessageCompleted(), pending.approvalId());
        if (outcome.messageEvent() != null) state = stateReducer.apply(state, outcome.messageEvent());
        AgentEvent completed = outcome.stateEvent();
        checkpointService.checkpointIfNeeded(completed, stateReducer.apply(state, completed));
    }

    public void recover(Long runId, String traceId, AgentRunLease lease) {
        if (pendingApprovals.findApprovedByRunId(runId) != null
                || pendingApprovals.findRejectedByRunId(runId) != null) {
            resume(runId, traceId, lease);
            return;
        }

        AgentRun run = runRepository.findRun(runId);
        AgentRunInput input = runRepository.findInput(runId);
        if (run == null || input == null) throw new IllegalArgumentException("Agent run not found: " + runId);
        AgentRuntimeState state = recoveryService.recover(runId);
        if (state != null && "DONE".equals(state.status())) {
            leaseService.complete(lease);
            return;
        }
        if (state != null && "FAILED".equals(state.status())) {
            leaseService.failTerminal(lease, "AGENT_RUN_RECOVERED_FAILED_EVENT",
                    "Run failure event was durable before the previous worker stopped");
            return;
        }
        if (state == null || state.artifactRefs().isEmpty()) {
            execute(runId, traceId, lease);
            return;
        }

        var contextArtifact = contextArtifacts.loadContextForRun(runId, state.artifactRefs());
        if (run.contextEpochId() == null || !run.contextEpochId().equals(contextArtifact.contextEpochId())) {
            throw new IllegalStateException("Run Context Epoch does not match its immutable artifact");
        }
        if (!continueIfDependenciesCurrent(run, input, contextArtifact, lease, traceId)) return;
        var promptArtifact = contextArtifacts.loadPromptPlanForRun(runId, state.artifactRefs());
        if (promptArtifact.schemaVersion() < 2 || promptArtifact.messages().isEmpty()) {
            throw new IllegalStateException("Run prompt artifact has no recoverable conversation snapshot: " + runId);
        }
        Long modelConfigId = extractModelConfigIdFromSnapshot(input.modelSnapshotJson());
        AgentLlmExecutionConfig executionConfig = modelConfigId == null
                ? AgentLlmExecutionConfig.builder().build()
                : modelRoutingService.resolveExecutionConfig(run.ownerUserId(), modelConfigId, traceId);
        leaseService.assertOwned(lease);
        AgentRunLoopRequest loopRequest = new AgentRunLoopRequest(
                runId, run.projectId(), run.sessionId(), run.turnId(), traceId,
                promptArtifact.messages(), resolveToolSchemas(promptArtifact), executionConfig, run.ownerUserId(),
                lease.executionToken());
        AgentRunLoopResult result = continuations.loadLatestForRun(runId, state.artifactRefs())
                .map(continuation -> llmLoop.resume(loopRequest, continuation))
                .orElseGet(() -> llmLoop.execute(loopRequest));
        finishRecoveredRun(runId, result, state, lease);
    }

    private boolean continueIfDependenciesCurrent(AgentRun run, AgentRunInput input,
                                                  AgentRunContextArtifactService.ResolvedArtifact artifact,
                                                  AgentRunLease lease, String traceId) {
        var validation = dependencyValidator.validate(run, input, artifact);
        if (validation.current()) return true;
        stateTransitions.supersede(lease, run, input, traceId, validation.changedFields());
        return false;
    }

    private void finishRecoveredRun(Long runId, AgentRunLoopResult result,
                                    AgentRuntimeState state, AgentRunLease lease) {
        if (result.status() == AgentRunLoopResult.Status.WAITING_APPROVAL) {
            AgentEvent waiting = stateTransitions.waitingApproval(lease, result.approvalId(), null).stateEvent();
            checkpointService.checkpointIfNeeded(waiting, stateReducer.apply(state, waiting));
            return;
        }
        if (result.status() == AgentRunLoopResult.Status.FAILED) {
            AgentEvent failed = stateTransitions.failed(lease, result.finalAssistantText(), null).stateEvent();
            checkpointService.checkpointIfNeeded(failed, stateReducer.apply(state, failed));
            return;
        }
        var outcome = stateTransitions.completed(lease, result.finalAssistantText(), result.tokenUsage(),
                state.assistantMessageCompleted(), null);
        if (outcome.messageEvent() != null) state = stateReducer.apply(state, outcome.messageEvent());
        AgentEvent completed = outcome.stateEvent();
        checkpointService.checkpointIfNeeded(completed, stateReducer.apply(state, completed));
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
            Object modelConfigId = jsonCodec.readObject(modelSnapshotJson).get("modelConfigId");
            long value = positiveLong(modelConfigId);
            return value > 0L ? value : null;
        } catch (RuntimeException ignored) {
            // ignore parse failures
        }
        return null;
    }

    private List<AgentLlmToolSchema> resolveToolSchemas(
            AgentRunContextArtifactService.PromptArtifact artifact
    ) {
        return artifact.plan().toolSchemas();
    }

    private long positiveLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}
