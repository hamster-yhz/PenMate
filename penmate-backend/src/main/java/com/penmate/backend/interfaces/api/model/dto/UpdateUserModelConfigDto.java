package com.penmate.backend.interfaces.api.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 更新用户模型配置入参。
 */
@Data
public class UpdateUserModelConfigDto {

    @Pattern(regexp = "^[1-9]\\d*$", message = "providerId must be greater than 0")
    @JsonDeserialize(using = StringIdOnlyDeserializer.class)
    private String providerId;

    private String modelName;

    private String baseUrl;

    private String modelCategory;

    private String apiKey;

    private String status;
}
