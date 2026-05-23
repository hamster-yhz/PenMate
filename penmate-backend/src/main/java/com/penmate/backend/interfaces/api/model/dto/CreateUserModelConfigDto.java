package com.penmate.backend.interfaces.api.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

/**
 * 新增用户模型配置入参。
 */
@Data
public class CreateUserModelConfigDto {

    @NotBlank
    @Pattern(regexp = "^[1-9]\\d*$", message = "providerId must be greater than 0")
    @JsonDeserialize(using = StringIdOnlyDeserializer.class)
    private String providerId;

    @NotBlank
    private String modelName;

    private String baseUrl;

    @NotBlank
    private String modelCategory;

    @NotBlank
    private String apiKey;

    @PositiveOrZero
    private Integer contextWindowTurns;

    @Positive
    private Integer maxContextTokens;

    private String status;
}
