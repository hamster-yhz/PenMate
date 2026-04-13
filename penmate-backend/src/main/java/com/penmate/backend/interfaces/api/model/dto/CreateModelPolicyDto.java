package com.penmate.backend.interfaces.api.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class CreateModelPolicyDto {

    @NotBlank
    private String policyName;

    @NotBlank
    private String scene;

    @NotNull
    private Long providerModelId;

    private Long userKeyId;
    private BigDecimal temperature;
    private BigDecimal topP;
    private Integer maxTokens;
    private String fallbackPolicyJson;
    private Boolean isDefault;

    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public Long getProviderModelId() {
        return providerModelId;
    }

    public void setProviderModelId(Long providerModelId) {
        this.providerModelId = providerModelId;
    }

    public Long getUserKeyId() {
        return userKeyId;
    }

    public void setUserKeyId(Long userKeyId) {
        this.userKeyId = userKeyId;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public void setTemperature(BigDecimal temperature) {
        this.temperature = temperature;
    }

    public BigDecimal getTopP() {
        return topP;
    }

    public void setTopP(BigDecimal topP) {
        this.topP = topP;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }

    public String getFallbackPolicyJson() {
        return fallbackPolicyJson;
    }

    public void setFallbackPolicyJson(String fallbackPolicyJson) {
        this.fallbackPolicyJson = fallbackPolicyJson;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }
}

