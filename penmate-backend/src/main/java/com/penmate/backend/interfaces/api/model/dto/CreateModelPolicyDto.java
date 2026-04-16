package com.penmate.backend.interfaces.api.model.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Data

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

}

