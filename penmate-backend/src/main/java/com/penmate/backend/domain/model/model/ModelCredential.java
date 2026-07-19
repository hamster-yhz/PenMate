package com.penmate.backend.domain.model.model;

import lombok.Data;

@Data
public class ModelCredential {
    private Long credentialId;
    private Long modelConfigId;
    private Long ownerUserId;
    private Long providerId;
    private String encryptedApiKey;
    private String maskedApiKey;
    private String status;
}
