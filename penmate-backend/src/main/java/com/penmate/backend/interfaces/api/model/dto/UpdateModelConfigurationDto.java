package com.penmate.backend.interfaces.api.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateModelConfigurationDto {
    @Pattern(regexp = "^[1-9]\\d*$", message = "providerId must be greater than 0")
    @JsonDeserialize(using = StringIdOnlyDeserializer.class)
    private String providerId;
    @Size(max = 120) private String displayName;
    @Size(max = 120) private String modelName;
    @Size(max = 500) private String baseUrl;
    @Pattern(regexp = "COSINE|INNER_PRODUCT|L2") private String distanceMetric;
    private String apiKey;
    @PositiveOrZero private Integer contextWindowTurns;
    @Positive private Integer maxContextTokens;
    @Pattern(regexp = "ACTIVE|DISABLED") private String status;
}
