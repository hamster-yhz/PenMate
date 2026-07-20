package com.penmate.backend.interfaces.api.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProbeEmbeddingDimensionDto {
    @Pattern(regexp = "^[1-9]\\d*$", message = "modelConfigId must be greater than 0")
    @JsonDeserialize(using = StringIdOnlyDeserializer.class)
    private String modelConfigId;
    @Pattern(regexp = "^[1-9]\\d*$", message = "providerId must be greater than 0")
    @JsonDeserialize(using = StringIdOnlyDeserializer.class)
    private String providerId;
    @Size(max = 120) private String modelName;
    @Size(max = 500) private String baseUrl;
    @Positive @Max(4000) private Integer embeddingDimensions;
    private String apiKey;
}
