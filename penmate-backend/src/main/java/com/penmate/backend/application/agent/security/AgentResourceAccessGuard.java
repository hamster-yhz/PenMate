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
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class AgentResourceAccessGuard {

    private final ProjectAccessAuthorizer projectAccess;
    private final AgentSessionRepository sessions;
    private final AgentRunRepository runs;
    private final ApprovalRequestRepository approvals;

    public AgentResourceAccessGuard(ProjectAccessAuthorizer projectAccess,
                                    AgentSessionRepository sessions,
                                    AgentRunRepository runs,
                                    ApprovalRequestRepository approvals) {
        this.projectAccess = projectAccess;
        this.sessions = sessions;
        this.runs = runs;
        this.approvals = approvals;
    }

    public NovelProject requireProject(Long projectId, Long actorUserId) {
        return projectAccess.requireOwnedProject(projectId, actorUserId);
    }

    public AgentSession requireSession(Long projectId, Long sessionId, Long actorUserId) {
        NovelProject project = requireProject(projectId, actorUserId);
        AgentSession session = sessionId == null ? null : sessions.findSession(project.getProjectId(), sessionId);
        if (session == null
                || !Objects.equals(session.getSessionId(), sessionId)
                || !Objects.equals(session.getProjectId(), projectId)
                || !Objects.equals(session.getOwnerUserId(), actorUserId)) {
            throw BusinessException.notFound("Agent session not found");
        }
        return session;
    }

    public AgentRun requireRun(Long projectId, Long runId, Long actorUserId) {
        NovelProject project = requireProject(projectId, actorUserId);
        return requireRunInProject(project, runId, actorUserId);
    }

    private AgentRun requireRunInProject(NovelProject project, Long runId, Long actorUserId) {
        AgentRun run = runId == null ? null : runs.findRun(runId);
        if (run == null
                || !Objects.equals(run.projectId(), project.getProjectId())
                || !Objects.equals(run.ownerUserId(), actorUserId)) {
            throw BusinessException.notFound("Agent Run not found");
        }
        AgentSession session = sessions.findSession(run.projectId(), run.sessionId());
        if (session == null
                || !Objects.equals(session.getSessionId(), run.sessionId())
                || !Objects.equals(session.getProjectId(), run.projectId())
                || !Objects.equals(session.getOwnerUserId(), actorUserId)) {
            throw BusinessException.notFound("Agent Run not found");
        }
        return run;
    }

    public ApprovalRequest requireApproval(Long projectId, Long approvalId, Long actorUserId) {
        NovelProject project = requireProject(projectId, actorUserId);
        ApprovalRequest approval = approvalId == null ? null : approvals.findByApprovalRequestId(approvalId);
        if (approval == null
                || !Objects.equals(approval.getApprovalRequestId(), approvalId)
                || !Objects.equals(approval.getProjectId(), project.getProjectId())) {
            throw BusinessException.notFound("Approval request not found");
        }
        if (approval.getRunId() != null) {
            requireRunInProject(project, approval.getRunId(), actorUserId);
        }
        return approval;
    }
}
