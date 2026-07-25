package com.penmate.backend.domain.novel.importing;

import java.time.Instant;

public class NovelImportSession {
    private Long sessionId;
    private Long ownerUserId;
    private String originalFilename;
    private NovelImportDraft draft;
    private String status;
    private Long projectId;
    private Long jobId;
    private Integer checkpointChapter;
    private Integer totalChapters;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public NovelImportDraft getDraft() { return draft; }
    public void setDraft(NovelImportDraft draft) { this.draft = draft; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public Integer getCheckpointChapter() { return checkpointChapter; }
    public void setCheckpointChapter(Integer checkpointChapter) { this.checkpointChapter = checkpointChapter; }
    public Integer getTotalChapters() { return totalChapters; }
    public void setTotalChapters(Integer totalChapters) { this.totalChapters = totalChapters; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
