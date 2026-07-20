package com.penmate.backend.domain.agent.model;

import java.time.Instant;

public class AgentSession {

    private Long id;
    private final Long sessionId;
    private final Long projectId;
    private final Long ownerUserId;
    private String title;
    private String sessionStatus;
    private Long boundStyleId;
    private Long activeContextEpochId;
    private Long lastTurnId;
    private Long lastRunId;
    private String lastRunStatus;
    private Instant resumedAt;
    private Instant createdAt;
    private Instant updatedAt;

    private AgentSession(Long sessionId, Long projectId, Long ownerUserId, String title, String sessionStatus) {
        this.sessionId = sessionId;
        this.projectId = projectId;
        this.ownerUserId = ownerUserId;
        this.title = title;
        this.sessionStatus = sessionStatus;
    }

    public static AgentSession active(Long sessionId, Long projectId, Long ownerUserId, String title) {
        return new AgentSession(sessionId, projectId, ownerUserId, title, "ACTIVE");
    }

    public static AgentSession summaryOf(AgentSession source) {
        AgentSession copied = new AgentSession(
                source.sessionId,
                source.projectId,
                source.ownerUserId,
                source.title,
                source.sessionStatus
        );
        copied.id = source.id;
        copied.boundStyleId = source.boundStyleId;
        copied.activeContextEpochId = source.activeContextEpochId;
        copied.lastTurnId = source.lastTurnId;
        copied.lastRunId = source.lastRunId;
        copied.lastRunStatus = source.lastRunStatus;
        copied.resumedAt = source.resumedAt;
        copied.createdAt = source.createdAt;
        copied.updatedAt = source.updatedAt;
        return copied;
    }

    public void attachRunningRun(Long runId) {
        if (runId == null) {
            throw new IllegalArgumentException("runId must not be null");
        }
        if (this.lastRunId != null && !this.lastRunId.equals(runId)) {
            throw new IllegalStateException("session already has active run");
        }
        this.lastRunId = runId;
        this.lastRunStatus = "RUNNING";
    }

    public void bindStyle(Long boundStyle) {
        this.boundStyleId = boundStyle;
    }

    public void markLastRunStatus(String lastRunStatus) {
        this.lastRunStatus = lastRunStatus;
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getTitle() {
        return title;
    }

    public String getSessionStatus() {
        return sessionStatus;
    }

    public String getStatus() {
        return sessionStatus;
    }

    public Long getBoundStyleId() {
        return boundStyleId;
    }

    public Long getBoundStyle() {
        return boundStyleId;
    }

    public Long getActiveContextEpochId() {
        return activeContextEpochId;
    }

    public Long getLastTurnId() {
        return lastTurnId;
    }

    public Long getLastRunId() {
        return lastRunId;
    }

    public String getLastRunStatus() {
        return lastRunStatus;
    }

    public Instant getResumedAt() {
        return resumedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
