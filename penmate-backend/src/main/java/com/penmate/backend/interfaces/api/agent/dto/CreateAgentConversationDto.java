package com.penmate.backend.interfaces.api.agent.dto;

import jakarta.validation.constraints.NotNull;

public class CreateAgentConversationDto {

    @NotNull
    private Long userId;
    private String title;
    private String contextScopeJson;
    private String status;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContextScopeJson() {
        return contextScopeJson;
    }

    public void setContextScopeJson(String contextScopeJson) {
        this.contextScopeJson = contextScopeJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

