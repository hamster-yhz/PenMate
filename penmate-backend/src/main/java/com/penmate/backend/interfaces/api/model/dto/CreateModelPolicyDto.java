package com.penmate.backend.interfaces.api.model.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

@Data

public class CreateModelPolicyDto {

    @NotBlank
    private String policyName;

    @NotBlank
    private String scene;

    private Long providerModelId;

    @NotBlank
    private String modelName;

    private String baseUrl;
    private Long userKeyId;
    private Long officialKeyId;
    private BigDecimal temperature;
    private BigDecimal topP;
    private Integer maxTokens;
    private String fallbackPolicyJson;
    private Boolean isDefault;

}

