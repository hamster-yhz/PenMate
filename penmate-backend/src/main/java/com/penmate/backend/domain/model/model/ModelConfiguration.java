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
    private Integer embeddingDimensions;
    private Integer contextWindowTurns;
    private Integer maxContextTokens;
    private Integer maxOutputTokens;
    private String contextCapacitySource;
    private String contextCapacitySourceUrl;
    private Instant contextCapacityVerifiedAt;
    private String maskedApiKey;
    private String credentialStatus;
    private String status;
    private String lastTestStatus;
    private Integer lastTestLatencyMs;
    private String lastTestError;
    private Instant lastTestedAt;
    private Long createdBy;
    private Long updatedBy;
    private Instant createdAt;
    private Instant updatedAt;
}
