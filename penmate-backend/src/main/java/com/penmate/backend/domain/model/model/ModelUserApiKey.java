package com.penmate.backend.domain.model.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data

public class ModelUserApiKey {
    private Long id;
    private Long userId;
    private Long providerId;
    private String keyName;
    private String encryptedApiKey;
    private String maskedApiKey;
    private Boolean isDefault;
    private LocalDateTime lastUsedAt;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

}
