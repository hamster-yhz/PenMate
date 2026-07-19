package com.penmate.backend.domain.model.model;

import lombok.Data;

import java.time.Instant;

@Data
public class ModelConfiguration {
    private Long modelConfigId;
    private String scopeType;
    private Long ownerUserId;
    private Long providerId;
    private String providerCode;
    private String providerName;
    private String providerBaseUrl;
    private String providerAuthType;
    private String protocolCode;
    private String displayName;
    private String modelType;
    private String modelName;
    private String baseUrl;
    private String distanceMetric;
    private Integer contextWindowTurns;
    private Integer maxContextTokens;
    private String maskedApiKey;
    private String credentialStatus;
    private String status;
    private Long createdBy;
    private Long updatedBy;
    private Instant createdAt;
    private Instant updatedAt;
}
