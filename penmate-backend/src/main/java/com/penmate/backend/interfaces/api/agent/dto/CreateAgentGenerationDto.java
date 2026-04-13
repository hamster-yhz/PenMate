package com.penmate.backend.interfaces.api.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateAgentGenerationDto {

    @NotNull
    private Long conversationId;
    private Long chapterId;
    @NotBlank
    private String taskType;
    private String promptSnapshot;
    private String styleProfileSnapshot;
    private String pluginSnapshot;

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Long getChapterId() {
        return chapterId;
    }

    public void setChapterId(Long chapterId) {
        this.chapterId = chapterId;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getPromptSnapshot() {
        return promptSnapshot;
    }

    public void setPromptSnapshot(String promptSnapshot) {
        this.promptSnapshot = promptSnapshot;
    }

    public String getStyleProfileSnapshot() {
        return styleProfileSnapshot;
    }

    public void setStyleProfileSnapshot(String styleProfileSnapshot) {
        this.styleProfileSnapshot = styleProfileSnapshot;
    }

    public String getPluginSnapshot() {
        return pluginSnapshot;
    }

    public void setPluginSnapshot(String pluginSnapshot) {
        this.pluginSnapshot = pluginSnapshot;
    }
}

