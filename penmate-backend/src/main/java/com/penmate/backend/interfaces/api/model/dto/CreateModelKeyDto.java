package com.penmate.backend.interfaces.api.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateModelKeyDto {

    @NotBlank
    @Pattern(regexp = "^[1-9]\\d*$", message = "providerId must be greater than 0")
    @JsonDeserialize(using = StringIdOnlyDeserializer.class)
    private String providerId;

    @NotBlank
    private String keyName;

    @NotBlank
    private String apiKey;

    private Boolean isDefault;
    private String status;
}
