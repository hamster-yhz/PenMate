package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.context.AgentContextEpochService;
import com.penmate.backend.application.agent.context.AgentRunContextArtifactService;
import com.penmate.backend.application.agent.context.AgentRunContextResolutionService;
import com.penmate.backend.application.agent.context.ContextPackage;
import com.penmate.backend.application.agent.context.StoryBibleRouteDecision;
import com.penmate.backend.application.agent.context.StoryBibleRoutingMode;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.prompt.PromptComposer;
import com.penmate.backend.application.agent.prompt.PromptPlan;
import com.penmate.backend.domain.agent.run.model.AgentEvent;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;
import com.penmate.backend.domain.agent.run.model.AgentRuntimeState;
import com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRunExecutorTest {

    @Mock private AgentRunRepository runRepository;
    @Mock private AgentRunEventPublisher eventPublisher;
    @Mock private AgentRunContextResolutionService contextResolutionService;
    @Mock private PromptComposer promptComposer;
    @Mock private AgentRunLlmLoop llmLoop;
    @Mock private AgentModelRoutingService modelRoutingService;
    @Mock private AgentRuntimeStateReducer stateReducer;
    @Mock private AgentCheckpointService checkpointService;
    @Mock private AgentRunPendingApprovalRepository pendingApprovals;
    @Mock private AgentRunContextArtifactService contextArtifacts;
    @Mock private AgentRunRecoveryService recoveryService;

    @Test
    void executor_routes_context_without_full_preflight_then_executes_run() {
        when(runRepository.findInput(70001L)).thenReturn(runInput());
        when(runRepository.findRun(70001L)).thenReturn(run());
        when(modelRoutingService.resolveExecutionConfig(anyLong(), anyLong(), anyString()))
                .thenReturn(AgentLlmExecutionConfig.builder().build());
        when(contextResolutionService.resolveInitial(any(), any(), any(), any(), any())).thenReturn(contextRoutingResult());
        when(promptComposer.compose(any(), any(), eq("Write a suspense opening."))).thenReturn(promptPlan());
        when(contextArtifacts.savePromptPlan(anyLong(), any(), any()))
                .thenReturn(new AgentRunContextArtifactService.ArtifactRef(89L, "prompt", "hash", 10));
        when(llmLoop.execute(any())).thenReturn(AgentRunLoopResult.completed("completed", new LlmTokenUsage(10, 5, 15)));
        when(eventPublisher.publish(any(), any(), any())).thenReturn(event());

        executor().execute(70001L, "trace-1");

        verify(eventPublisher).publish(eq(70001L), eq("run.phase.changed"), containsText("routing"));
        verify(eventPublisher).publish(eq(70001L), eq("run.phase.changed"), containsText("epoch_binding"));
        verify(eventPublisher).publish(eq(70001L), eq("context.epoch.bound"), any());
        verify(eventPublisher).publish(eq(70001L), eq("turn.route.completed"), any());
        verify(eventPublisher).publish(eq(70001L), eq("run.completed"), any());
    }

    @Test
    void executor_treats_null_model_config_snapshot_as_default_config() {
        when(runRepository.findInput(70001L)).thenReturn(new AgentRunInput(
                70001L, "Write a suspense opening.", "WRITE", 30001L, "selected text",
                "{\"styleId\":81}", "{\"operatorId\":920001,\"modelConfigId\":null}", null, "hash-70001"));
        when(runRepository.findRun(70001L)).thenReturn(run());
        when(contextResolutionService.resolveInitial(any(), any(), any(), any(), any())).thenReturn(contextRoutingResult());
        when(promptComposer.compose(any(), any(), eq("Write a suspense opening."))).thenReturn(promptPlan());
        when(contextArtifacts.savePromptPlan(anyLong(), any(), any()))
                .thenReturn(new AgentRunContextArtifactService.ArtifactRef(89L, "prompt", "hash", 10));
        when(llmLoop.execute(any())).thenReturn(AgentRunLoopResult.completed("completed", new LlmTokenUsage(10, 5, 15)));
        when(eventPublisher.publish(any(), any(), any())).thenReturn(event());

        executor().execute(70001L, "trace-1");

        verify(modelRoutingService, never()).resolveExecutionConfig(anyLong(), anyLong(), anyString());
        verify(eventPublisher).publish(eq(70001L), eq("run.completed"), any());
    }

    @Test
    void resume_should_load_checkpoint_and_artifact_without_rebuilding_context() {
        AgentRun resumed = new AgentRun(70001L, 10001L, 20001L, 30001L, 920001L,
                "RUNNING", "waiting_approval", 99L, null, 10L, null, "trace-1", null, null);
        when(runRepository.findRun(70001L)).thenReturn(resumed);
        when(runRepository.findInput(70001L)).thenReturn(runInput());
        when(recoveryService.recover(70001L))
                .thenReturn(AgentRuntimeState.empty(70001L)
                        .withArtifactAdded(89L, 4L)
                        .withArtifactAdded(88L, 5L));
        when(contextArtifacts.loadContextForRun(70001L, List.of(89L, 88L)))
                .thenReturn(new AgentRunContextArtifactService.ResolvedArtifact(
                1, 70001L, 99L,
                new StoryBibleRouteDecision(StoryBibleRoutingMode.RETRIEVAL, List.of(1L), Map.of(), false, 0L, true, List.of()),
                new ContextPackage(List.of(), List.of(), List.of(), List.of(), List.of(), "", "chapter:30001"), List.of()));
        AgentRunPendingApproval pending = new AgentRunPendingApproval(
                1L, 2L, 3L, 70001L, 10001L, 20001L, 30001L, "call-1", "story_bible_update",
                "{}", "{}", "[]", "key", "APPROVED", 920001L, "trace-1", null, null);
        when(pendingApprovals.findApprovedByRunId(70001L)).thenReturn(pending);
        when(modelRoutingService.resolveExecutionConfig(anyLong(), anyLong(), anyString()))
                .thenReturn(AgentLlmExecutionConfig.builder().build());
        when(llmLoop.resumeApproved(any(), eq(pending)))
                .thenReturn(AgentRunLoopResult.completed("done", new LlmTokenUsage(1, 1, 2)));
        when(eventPublisher.publish(any(), any(), any())).thenReturn(event());

        executor().resume(70001L, "trace-1");

        verify(contextResolutionService, never()).resolveInitial(any(), any(), any(), any(), any());
        verify(llmLoop).resumeApproved(any(), eq(pending));
        verify(eventPublisher).publish(eq(70001L), eq("run.completed"), any());
    }

    private AgentRunExecutor executor() {
        return new AgentRunExecutor(runRepository, eventPublisher, contextResolutionService, promptComposer,
                llmLoop, modelRoutingService, stateReducer, checkpointService, pendingApprovals, contextArtifacts,
                recoveryService);
    }

    private AgentRun run() {
        return new AgentRun(70001L, 10001L, 20001L, 30001L, 920001L,
                "PENDING", "created", null, null, 0L, null, "trace-1", null, null);
    }

    private AgentRunInput runInput() {
        return new AgentRunInput(70001L, "Write a suspense opening.", "WRITE", 30001L,
                "selected text", "{\"styleId\":81}", "{\"modelConfigId\":1001}", null, "hash-70001");
    }

    private AgentEvent event() {
        return new AgentEvent(1L, 70001L, 101L, 20001L, 30001L, 1L, 1, "run.phase.changed", "{}", null);
    }

    private AgentRunContextResolutionService.Resolution contextRoutingResult() {
        var epoch = new com.penmate.backend.domain.agent.context.model.AgentContextEpoch(
                99L, 20001L, 1, "hash", 1L, 1L, 30001L, 0L, "RETRIEVAL", null, 0L,
                "prompt", "skills", "tools", "key", "hash", 10L, null, null);
        return new AgentRunContextResolutionService.Resolution(
                new AgentContextEpochService.Binding(epoch, false),
                new ContextPackage(List.of("story-bible"), List.of(), List.of(), List.of(), List.of(),
                        "{\"styleId\":81}", "chapter:30001"),
                new StoryBibleRouteDecision(StoryBibleRoutingMode.RETRIEVAL, List.of(1L), Map.of(),
                        false, 0L, true, List.of()),
                new AgentRunContextArtifactService.ArtifactRef(88L, "key", "hash", 10),
                new com.penmate.backend.application.agent.context.AgentContextCatalogHashService.Hashes("p", "s", "t"),
                "core");
    }

    private PromptPlan promptPlan() {
        return new PromptPlan(List.of(), List.of(), "default", "assembled prompt");
    }

    private Object containsText(String expected) {
        return argThat((ArgumentMatcher<Object>) payload -> payload != null && payload.toString().contains(expected));
    }
}
