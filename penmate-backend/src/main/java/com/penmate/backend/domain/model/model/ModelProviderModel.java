package com.penmate.backend.domain.model.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data

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

}

