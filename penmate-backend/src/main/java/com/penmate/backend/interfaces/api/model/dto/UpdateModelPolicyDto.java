package com.penmate.backend.interfaces.api.model.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data

public class UpdateModelPolicyDto {

    private String policyName;
    private String scene;
    private Long providerModelId;
    private Long userKeyId;
    private BigDecimal temperature;
    private BigDecimal topP;
    private Integer maxTokens;
    private String fallbackPolicyJson;
    private Boolean isDefault;

}

