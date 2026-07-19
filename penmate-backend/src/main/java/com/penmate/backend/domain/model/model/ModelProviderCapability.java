package com.penmate.backend.domain.model.model;

import lombok.Data;

@Data
public class ModelProviderCapability {
    private Long providerCapabilityId;
    private Long providerId;
    private String capabilityCode;
    private String protocolCode;
    private String status;
}
