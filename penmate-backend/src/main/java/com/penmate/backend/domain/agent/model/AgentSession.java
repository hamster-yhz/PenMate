package com.penmate.backend.domain.agent.model;

import java.time.LocalDateTime;

/**
 * 智能体会话聚合根。
 * <p>承载会话恢复所需的主体状态，并维护“同一会话同时最多一个运行中任务”的领域约束。</p>
 */
public class AgentSession {

    /** 数据库物理主键 ID。 */
    private Long id;
    /** 会话业务 ID。 */
    private final Long sessionId;
    /** 所属项目业务 ID。 */
    private final Long projectId;
    /** 会话所属用户业务 ID。 */
    private final Long ownerUserId;
    /** 会话标题。 */
    private String title;
    /** 会话状态，如 ACTIVE。 */
    private String sessionStatus;
    /** 当前绑定的风格业务 ID。 */
    private Long boundStyleId;
    /** 当前上下文版本号。 */
    private Integer activeContextVersion;
    /** 最后一个轮次业务 ID。 */
    private Long lastTurnId;
    /** 最近绑定的运行中任务业务 ID。 */
    private Long lastTaskId;
    /** 最近任务状态，作为 session summary 的 contract 语义暴露。 */
    private String lastTaskStatus;
    /** 最近恢复时间。 */
    private LocalDateTime resumedAt;
    /** 会话创建时间。 */
    private LocalDateTime createdAt;
    /** 会话更新时间。 */
    private LocalDateTime updatedAt;

    private AgentSession(Long sessionId, Long projectId, Long ownerUserId, String title, String sessionStatus) {
        this.sessionId = sessionId;
        this.projectId = projectId;
        this.ownerUserId = ownerUserId;
        this.title = title;
        this.sessionStatus = sessionStatus;
    }

    /**
     * 创建激活态会话。
     */
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
        copied.activeContextVersion = source.activeContextVersion;
        copied.lastTurnId = source.lastTurnId;
        copied.lastTaskId = source.lastTaskId;
        copied.lastTaskStatus = source.lastTaskStatus;
        copied.resumedAt = source.resumedAt;
        copied.createdAt = source.createdAt;
        copied.updatedAt = source.updatedAt;
        return copied;
    }

    /**
     * 绑定当前运行中的任务。
     * <p>同一会话在任一时刻只允许关联一个运行中任务。</p>
     *
     * @param taskId 任务业务 ID
     */
    public void attachRunningTask(Long taskId) {
        if (taskId == null) {
            throw new IllegalArgumentException("taskId must not be null");
        }
        if (this.lastTaskId != null && !this.lastTaskId.equals(taskId)) {
            throw new IllegalStateException("session already has running task");
        }
        this.lastTaskId = taskId;
        this.lastTaskStatus = "RUNNING";
    }

    /**
     * recovery contract: Session Summary.boundStyle
     */
    public void bindStyle(Long boundStyle) {
        this.boundStyleId = boundStyle;
    }

    /**
     * recovery contract: Session Summary.lastTaskStatus
     */
    public void markLastTaskStatus(String lastTaskStatus) {
        this.lastTaskStatus = lastTaskStatus;
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

    /**
     * recovery contract: Session Summary.status
     */
    public String getStatus() {
        return sessionStatus;
    }

    public Long getBoundStyleId() {
        return boundStyleId;
    }

    /**
     * recovery contract: Session Summary.boundStyle
     */
    public Long getBoundStyle() {
        return boundStyleId;
    }

    public Integer getActiveContextVersion() {
        return activeContextVersion;
    }

    public Long getLastTurnId() {
        return lastTurnId;
    }

    public Long getLastTaskId() {
        return lastTaskId;
    }

    /**
     * recovery contract: Session Summary.lastTaskStatus
     */
    public String getLastTaskStatus() {
        return lastTaskStatus;
    }

    public LocalDateTime getResumedAt() {
        return resumedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
