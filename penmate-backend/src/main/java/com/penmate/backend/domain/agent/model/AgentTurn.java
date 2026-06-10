package com.penmate.backend.domain.agent.model;

import java.time.LocalDateTime;

public class AgentTurn {

    private Long id;
    private Long sessionId;
    private Long turnId;
    private Integer turnSeq;
    private Long userMessageId;
    private Long assistantMessageId;
    private String turnStatus;
    private Long runId;
    private String resumeToken;
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

    public Long getRunId() {
        return runId;
    }

    public String getResumeToken() {
        return resumeToken;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
