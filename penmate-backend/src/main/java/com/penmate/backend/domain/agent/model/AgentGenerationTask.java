package com.penmate.backend.domain.agent.model;

import java.time.LocalDateTime;

public class AgentGenerationTask {
    private Long id;
    private Long projectId;
    private Long conversationId;
    private Long chapterId;
    private String taskType;
    private String promptSnapshot;
    private String styleProfileSnapshot;
    private String pluginSnapshot;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMsg;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getPromptSnapshot() { return promptSnapshot; }
    public void setPromptSnapshot(String promptSnapshot) { this.promptSnapshot = promptSnapshot; }
    public String getStyleProfileSnapshot() { return styleProfileSnapshot; }
    public void setStyleProfileSnapshot(String styleProfileSnapshot) { this.styleProfileSnapshot = styleProfileSnapshot; }
    public String getPluginSnapshot() { return pluginSnapshot; }
    public void setPluginSnapshot(String pluginSnapshot) { this.pluginSnapshot = pluginSnapshot; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

