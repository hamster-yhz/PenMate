package com.penmate.backend.interfaces.api.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateModelConfigurationDto {
    @NotBlank
    @Pattern(regexp = "^[1-9]\\d*$", message = "providerId must be greater than 0")
    @JsonDeserialize(using = StringIdOnlyDeserializer.class)
    private String providerId;
    @NotBlank @Size(max = 120) private String displayName;
    @NotBlank @Pattern(regexp = "CHAT|EMBEDDING") private String modelType;
    @NotBlank @Size(max = 120) private String modelName;
    @Size(max = 500) private String baseUrl;
    @Pattern(regexp = "COSINE|INNER_PRODUCT|L2") private String distanceMetric;
    @Positive @Max(4000) private Integer embeddingDimensions;
    private String apiKey;
    @PositiveOrZero private Integer contextWindowTurns;
    @Positive private Integer maxContextTokens;
    @Positive private Integer maxOutputTokens;
    @Pattern(regexp = "AUTO|NONE|MINIMAL|LOW|MEDIUM|HIGH|XHIGH|MAX") private String reasoningEffort;
    @Pattern(regexp = "AUTO|STANDARD|PRO|ADAPTIVE|DISABLED") private String reasoningMode;
    @Pattern(regexp = "AUTO|NONE|CONCISE|DETAILED") private String reasoningSummary;
    private Boolean autoDetectCapacity;
}
