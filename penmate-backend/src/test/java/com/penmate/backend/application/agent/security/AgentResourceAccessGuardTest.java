package com.penmate.backend.application.agent.security;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.novel.security.ProjectAccessAuthorizer;
import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import com.penmate.backend.domain.approval.model.ApprovalRequest;
import com.penmate.backend.domain.approval.repository.ApprovalRequestRepository;
import com.penmate.backend.domain.novel.model.NovelProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentResourceAccessGuardTest {

    @Mock private ProjectAccessAuthorizer projectAccess;
    @Mock private AgentSessionRepository sessions;
    @Mock private AgentRunRepository runs;
    @Mock private ApprovalRequestRepository approvals;

    private AgentResourceAccessGuard guard;

    @BeforeEach
    void setUp() {
        guard = new AgentResourceAccessGuard(projectAccess, sessions, runs, approvals);
    }

    @Test
    void owner_can_access_project_session_run_and_approval() {
        NovelProject project = project(101L, 201L);
        AgentSession session = AgentSession.active(301L, 101L, 201L, "Session");
        AgentRun run = run(401L, 101L, 301L, 201L);
        ApprovalRequest approval = approval(501L, 101L, 401L);
        when(projectAccess.requireOwnedProject(101L, 201L)).thenReturn(project);
        when(sessions.findSession(101L, 301L)).thenReturn(session);
        when(runs.findRun(401L)).thenReturn(run);
        when(approvals.findByApprovalRequestId(501L)).thenReturn(approval);

        assertThat(guard.requireProject(101L, 201L)).isSameAs(project);
        assertThat(guard.requireSession(101L, 301L, 201L)).isSameAs(session);
        assertThat(guard.requireRun(101L, 401L, 201L)).isSameAs(run);
        assertThat(guard.requireApproval(101L, 501L, 201L)).isSameAs(approval);
    }

    @Test
    void another_user_cannot_access_project_or_descendant_resources() {
        doThrow(BusinessException.notFound("Novel project not found"))
                .when(projectAccess).requireOwnedProject(101L, 202L);

        assertNotFound(() -> guard.requireProject(101L, 202L), "Novel project not found");
        assertNotFound(() -> guard.requireSession(101L, 301L, 202L), "Novel project not found");
        assertNotFound(() -> guard.requireRun(101L, 401L, 202L), "Novel project not found");
        assertNotFound(() -> guard.requireApproval(101L, 501L, 202L), "Novel project not found");

        verify(sessions, never()).findSession(101L, 301L);
        verify(runs, never()).findRun(401L);
        verify(approvals, never()).findByApprovalRequestId(501L);
    }

    @Test
    void run_from_project_b_cannot_be_accessed_through_project_a() {
        when(projectAccess.requireOwnedProject(101L, 201L)).thenReturn(project(101L, 201L));
        when(runs.findRun(401L)).thenReturn(run(401L, 102L, 302L, 201L));

        assertNotFound(() -> guard.requireRun(101L, 401L, 201L), "Agent Run not found");
        verify(sessions, never()).findSession(102L, 302L);
    }

    @Test
    void approval_from_project_b_cannot_be_accessed_through_project_a() {
        when(projectAccess.requireOwnedProject(101L, 201L)).thenReturn(project(101L, 201L));
        when(approvals.findByApprovalRequestId(501L)).thenReturn(approval(501L, 102L, 401L));

        assertNotFound(() -> guard.requireApproval(101L, 501L, 201L), "Approval request not found");
        verify(runs, never()).findRun(401L);
    }

    @Test
    void approval_reuses_the_verified_project_when_checking_its_run() {
        NovelProject project = project(101L, 201L);
        when(projectAccess.requireOwnedProject(101L, 201L)).thenReturn(project);
        when(approvals.findByApprovalRequestId(501L)).thenReturn(approval(501L, 101L, 401L));
        when(runs.findRun(401L)).thenReturn(run(401L, 101L, 301L, 201L));
        when(sessions.findSession(101L, 301L))
                .thenReturn(AgentSession.active(301L, 101L, 201L, "Session"));

        assertThat(guard.requireApproval(101L, 501L, 201L).getApprovalRequestId()).isEqualTo(501L);

        verify(projectAccess).requireOwnedProject(101L, 201L);
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String message) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .hasMessage(message)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("RESOURCE_NOT_FOUND");
    }

    private NovelProject project(Long projectId, Long ownerUserId) {
        NovelProject project = new NovelProject();
        project.setProjectId(projectId);
        project.setOwnerUserId(ownerUserId);
        return project;
    }

    private AgentRun run(Long runId, Long projectId, Long sessionId, Long ownerUserId) {
        return new AgentRun(runId, projectId, sessionId, 601L, ownerUserId,
                "RUNNING", "executing", 701L, null, 1L, null, "trace", null, null);
    }

    private ApprovalRequest approval(Long approvalId, Long projectId, Long runId) {
        ApprovalRequest approval = new ApprovalRequest();
        approval.setApprovalRequestId(approvalId);
        approval.setProjectId(projectId);
        approval.setRunId(runId);
        approval.setStatus("pending");
        return approval;
    }
}
