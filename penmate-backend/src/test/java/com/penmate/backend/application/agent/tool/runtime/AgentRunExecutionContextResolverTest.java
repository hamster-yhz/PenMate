package com.penmate.backend.application.agent.tool.runtime;

import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentRunInput;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRunExecutionContextResolverTest {

    @Mock private AgentRunRepository runs;
    @Mock private AgentSessionRepository sessions;
    @Mock private NovelGateway novels;

    private AgentRunExecutionContextResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new AgentRunExecutionContextResolver(runs, sessions, novels);
    }

    @Test
    void valid_run_and_execution_token_resolve_authoritative_context() {
        stubValidResources();

        AuthorizedAgentRunContext context = resolver.resolve(request(7L));

        assertThat(context).extracting(
                AuthorizedAgentRunContext::projectId,
                AuthorizedAgentRunContext::runId,
                AuthorizedAgentRunContext::sessionId,
                AuthorizedAgentRunContext::turnId,
                AuthorizedAgentRunContext::ownerUserId,
                AuthorizedAgentRunContext::executionToken)
                .containsExactly(101L, 401L, 301L, 601L, 201L, 7L);
        assertThat(context.input().chapterId()).isEqualTo(801L);
        assertThatCode(() -> resolver.assertExecutionOwned(context)).doesNotThrowAnyException();
    }

    @Test
    void stale_execution_token_is_rejected_before_resource_lookup() {
        when(runs.findRun(401L)).thenReturn(run());
        when(runs.findInput(401L)).thenReturn(input(401L));
        when(runs.ownsExecutionToken(org.mockito.ArgumentMatchers.eq(401L),
                org.mockito.ArgumentMatchers.eq(6L), any(Instant.class))).thenReturn(false);

        assertRejected(() -> resolver.resolve(request(6L)), "AGENT_RUN_EXECUTION_FENCED");
        verify(novels, never()).findProjectById(any());
        verify(sessions, never()).findSession(any(), any());
    }

    @Test
    void project_owner_mismatch_is_rejected() {
        stubValidResources();
        NovelProject project = project();
        project.setOwnerUserId(202L);
        when(novels.findProjectById(101L)).thenReturn(project);

        assertRejected(() -> resolver.resolve(request(7L)), "AGENT_RUN_RESOURCE_CONTEXT_MISMATCH");
    }

    @Test
    void session_project_or_owner_mismatch_is_rejected() {
        stubValidResources();
        when(sessions.findSession(101L, 301L))
                .thenReturn(AgentSession.active(301L, 102L, 202L, "Wrong session"));

        assertRejected(() -> resolver.resolve(request(7L)), "AGENT_RUN_RESOURCE_CONTEXT_MISMATCH");
    }

    @Test
    void immutable_input_from_another_run_is_rejected() {
        stubValidResources();
        when(runs.findInput(401L)).thenReturn(input(402L));

        assertRejected(() -> resolver.resolve(request(7L)), "AGENT_RUN_RESOURCE_CONTEXT_MISMATCH");
    }

    @Test
    void fabricated_authorized_context_is_rejected_before_token_check() {
        when(runs.findRun(401L)).thenReturn(run());
        AuthorizedAgentRunContext fabricated = AgentToolTestContext.context(
                999L, 401L, 301L, 601L, 201L, 701L, 7L, 801L, "trace");

        assertRejected(() -> resolver.assertExecutionOwned(fabricated),
                "AGENT_RUN_RESOURCE_CONTEXT_MISMATCH");
        verify(runs, never()).ownsExecutionToken(any(), any(), any());
    }

    private void stubValidResources() {
        when(runs.findRun(401L)).thenReturn(run());
        when(runs.findInput(401L)).thenReturn(input(401L));
        when(runs.ownsExecutionToken(org.mockito.ArgumentMatchers.eq(401L),
                org.mockito.ArgumentMatchers.eq(7L), any(Instant.class))).thenReturn(true);
        when(novels.findProjectById(101L)).thenReturn(project());
        when(sessions.findSession(101L, 301L))
                .thenReturn(AgentSession.active(301L, 101L, 201L, "Session"));
    }

    private void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String errorCode) {
        assertThatThrownBy(call)
                .isInstanceOf(AgentRunExecutionRejectedException.class)
                .extracting(error -> ((AgentRunExecutionRejectedException) error).errorCode())
                .isEqualTo(errorCode);
    }

    private ToolCallRequest request(Long token) {
        return new ToolCallRequest(401L, "story_bible_search", "{}", "idem", "call", token);
    }

    private AgentRun run() {
        return new AgentRun(401L, 101L, 301L, 601L, 201L,
                "RUNNING", "executing", 701L, null, 1L, null, "trace", null, null);
    }

    private AgentRunInput input(Long runId) {
        return new AgentRunInput(runId, "prompt", "WRITE", 801L,
                null, null, null, null, "hash");
    }

    private NovelProject project() {
        NovelProject project = new NovelProject();
        project.setProjectId(101L);
        project.setOwnerUserId(201L);
        return project;
    }
}
