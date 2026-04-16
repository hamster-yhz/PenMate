package com.penmate.backend.domain.model.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data

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

}

