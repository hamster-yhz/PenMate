package com.penmate.backend.domain.model.model;

import java.time.LocalDateTime;

public class ModelProviderModel {
    private Long id;
    private Long providerId;
    private String modelCode;
    private String modelName;
    private Integer contextWindow;
    private Integer maxOutput;
    private String pricingJson;
    private String status;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProviderId() { return providerId; }
    public void setProviderId(Long providerId) { this.providerId = providerId; }
    public String getModelCode() { return modelCode; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public Integer getContextWindow() { return contextWindow; }
    public void setContextWindow(Integer contextWindow) { this.contextWindow = contextWindow; }
    public Integer getMaxOutput() { return maxOutput; }
    public void setMaxOutput(Integer maxOutput) { this.maxOutput = maxOutput; }
    public String getPricingJson() { return pricingJson; }
    public void setPricingJson(String pricingJson) { this.pricingJson = pricingJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

