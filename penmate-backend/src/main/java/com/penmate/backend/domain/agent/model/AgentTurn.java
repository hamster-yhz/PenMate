package com.penmate.backend.domain.agent.model;

import java.time.LocalDateTime;

/**
 * 智能体会话中的单轮交互聚合。
 * <p>按契约仅引用主消息业务 ID，不在此处内嵌消息文本快照。</p>
 */
public class AgentTurn {

    /** 数据库物理主键 ID。 */
    private Long id;
    /** 所属会话业务 ID。 */
    private Long sessionId;
    /** 轮次业务 ID。 */
    private Long turnId;
    /** 轮次序号。 */
    private Integer turnSeq;
    /** 主用户消息业务 ID。 */
    private Long userMessageId;
    /** 主助手消息业务 ID。 */
    private Long assistantMessageId;
    /** 轮次状态。 */
    private String turnStatus;
    /** 该轮关联任务业务 ID。 */
    private Long taskId;
    /** 恢复令牌。 */
    private String resumeToken;
    /** 创建时间。 */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public Long getTurnId() {
        return turnId;
    }

    public Integer getTurnSeq() {
        return turnSeq;
    }

    public Long getUserMessageId() {
        return userMessageId;
    }

    public Long getAssistantMessageId() {
        return assistantMessageId;
    }

    public String getTurnStatus() {
        return turnStatus;
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getResumeToken() {
        return resumeToken;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
