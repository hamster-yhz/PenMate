package com.penmate.backend.domain.model.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ModelProjectPolicy {
    private Long id;
    private Long projectId;
    private String policyName;
    private String scene;
    private Long providerModelId;
    private Long userKeyId;
    private BigDecimal temperature;
    private BigDecimal topP;
    private Integer maxTokens;
    private String fallbackPolicyJson;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }
    public String getScene() { return scene; }
    public void setScene(String scene) { this.scene = scene; }
    public Long getProviderModelId() { return providerModelId; }
    public void setProviderModelId(Long providerModelId) { this.providerModelId = providerModelId; }
    public Long getUserKeyId() { return userKeyId; }
    public void setUserKeyId(Long userKeyId) { this.userKeyId = userKeyId; }
    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }
    public BigDecimal getTopP() { return topP; }
    public void setTopP(BigDecimal topP) { this.topP = topP; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public String getFallbackPolicyJson() { return fallbackPolicyJson; }
    public void setFallbackPolicyJson(String fallbackPolicyJson) { this.fallbackPolicyJson = fallbackPolicyJson; }
    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}

