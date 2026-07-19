package com.penmate.backend.application.agent.tool.runtime;

import com.penmate.backend.application.agent.context.AgentRunContextArtifactService;
import com.penmate.backend.application.agent.context.AgentRunDependencyValidator;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentToolMutationGuardTest {

    @Mock private AgentRunRepository runs;
    @Mock private AgentRunContextArtifactService contextArtifacts;
    @Mock private AgentRunDependencyValidator dependencies;

    private AgentToolMutationGuard guard;

    @BeforeEach
    void setUp() {
        guard = new AgentToolMutationGuard(runs, contextArtifacts, dependencies);
    }

    @Test
    void rejects_lost_execution_token_before_loading_dependencies() {
        ToolCallRequest request = request();
        when(runs.ownsExecutionToken(any(), any(), any(Instant.class))).thenReturn(false);

        assertThatThrownBy(() -> guard.assertExecutable(request, true))
                .isInstanceOf(AgentToolMutationGuard.Rejection.class)
                .extracting(failure -> ((AgentToolMutationGuard.Rejection) failure).errorCode())
                .isEqualTo("AGENT_RUN_EXECUTION_FENCED");
        verify(runs, never()).findRun(any());
    }

    @Test
    void read_only_call_only_requires_current_execution_token() {
        when(runs.ownsExecutionToken(any(), any(), any(Instant.class))).thenReturn(true);

        assertThatCode(() -> guard.assertExecutable(request(), false)).doesNotThrowAnyException();

        verify(contextArtifacts, never()).loadLatestContextForRun(any());
        verify(dependencies, never()).validate(any(), any(), any());
    }

    @Test
    void mutation_rejects_changed_dependency_manifest() {
        ToolCallRequest request = request();
        AgentRun run = run();
        AgentRunInput input = input();
        var artifact = artifact();
        when(runs.ownsExecutionToken(any(), any(), any(Instant.class))).thenReturn(true);
        when(runs.findRun(11L)).thenReturn(run);
        when(runs.findInput(11L)).thenReturn(input);
        when(contextArtifacts.loadLatestContextForRun(11L)).thenReturn(artifact);
        when(dependencies.validate(run, input, artifact)).thenReturn(
                new AgentRunDependencyValidator.Validation(false, null, null,
                        List.of("storyBibleRevision")));

        assertThatThrownBy(() -> guard.assertExecutable(request, true))
                .isInstanceOf(AgentToolMutationGuard.Rejection.class)
                .hasMessageContaining("storyBibleRevision")
                .extracting(failure -> ((AgentToolMutationGuard.Rejection) failure).errorCode())
                .isEqualTo("AGENT_RUN_DEPENDENCY_CHANGED");
    }

    @Test
    void mutation_executes_when_token_epoch_and_dependencies_are_current() {
        ToolCallRequest request = request();
        AgentRun run = run();
        AgentRunInput input = input();
        var artifact = artifact();
        when(runs.ownsExecutionToken(any(), any(), any(Instant.class))).thenReturn(true);
        when(runs.findRun(11L)).thenReturn(run);
        when(runs.findInput(11L)).thenReturn(input);
        when(contextArtifacts.loadLatestContextForRun(11L)).thenReturn(artifact);
        when(dependencies.validate(run, input, artifact)).thenReturn(
                new AgentRunDependencyValidator.Validation(true, null, null, List.of()));

        assertThatCode(() -> guard.assertExecutable(request, true)).doesNotThrowAnyException();
    }

    private ToolCallRequest request() {
        return new ToolCallRequest(1L, 11L, 2L, 3L, "story_bible_update", "{}", 4L,
                "trace", "{}", "idem", 1, "call", "[]", "[]", null, null, 7L);
    }

    private AgentRun run() {
        return new AgentRun(11L, 1L, 2L, 3L, 4L, "RUNNING", "executing", 9L,
                null, 0L, null, "trace", null, null);
    }

    private AgentRunInput input() {
        return new AgentRunInput(11L, "prompt", "WRITE", 5L, null, null, null, null, "hash");
    }

    private AgentRunContextArtifactService.ResolvedArtifact artifact() {
        return new AgentRunContextArtifactService.ResolvedArtifact(2, 11L, 9L, null, null,
                List.of(), null);
    }
}
