package com.penmate.backend.interfaces.api.model.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data

public class CreateModelKeyDto {

    @NotNull
    private Long providerId;

    @NotBlank
    private String keyName;

    @NotBlank
    private String apiKey;

    private Boolean isDefault;
    private String status;

}

