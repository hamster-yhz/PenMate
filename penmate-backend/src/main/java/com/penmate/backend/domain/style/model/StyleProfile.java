package com.penmate.backend.domain.style.model;

import java.time.LocalDateTime;

public class StyleProfile {
    private Long id;
    private Long projectId;
    private String name;
    private Boolean isDefault;
    private String pace;
    private String tone;
    private String narrativeFocus;
    private String promptTemplate;
    private String sampleText;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
    public String getPace() { return pace; }
    public void setPace(String pace) { this.pace = pace; }
    public String getTone() { return tone; }
    public void setTone(String tone) { this.tone = tone; }
    public String getNarrativeFocus() { return narrativeFocus; }
    public void setNarrativeFocus(String narrativeFocus) { this.narrativeFocus = narrativeFocus; }
    public String getPromptTemplate() { return promptTemplate; }
    public void setPromptTemplate(String promptTemplate) { this.promptTemplate = promptTemplate; }
    public String getSampleText() { return sampleText; }
    public void setSampleText(String sampleText) { this.sampleText = sampleText; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}

