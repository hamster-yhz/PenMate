package com.penmate.backend.interfaces.api.agent.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateAgentMessageDto {

    @NotBlank
    private String role;
    private String userMessageType;
    @NotBlank
    private String contentMd;
    private String attachmentsJson;
    private String toolCallsJson;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getUserMessageType() {
        return userMessageType;
    }

    public void setUserMessageType(String userMessageType) {
        this.userMessageType = userMessageType;
    }

    public String getContentMd() {
        return contentMd;
    }

    public void setContentMd(String contentMd) {
        this.contentMd = contentMd;
    }

    public String getAttachmentsJson() {
        return attachmentsJson;
    }

    public void setAttachmentsJson(String attachmentsJson) {
        this.attachmentsJson = attachmentsJson;
    }

    public String getToolCallsJson() {
        return toolCallsJson;
    }

    public void setToolCallsJson(String toolCallsJson) {
        this.toolCallsJson = toolCallsJson;
    }
}

