package com.penmate.backend.interfaces.api.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DiscoverModelsDto {
    @Pattern(regexp = "^[1-9]\\d*$", message = "modelConfigId must be greater than 0")
    @JsonDeserialize(using = StringIdOnlyDeserializer.class)
    private String modelConfigId;
    @Pattern(regexp = "^[1-9]\\d*$", message = "providerId must be greater than 0")
    @JsonDeserialize(using = StringIdOnlyDeserializer.class)
    private String providerId;
    @Pattern(regexp = "CHAT|EMBEDDING")
    private String modelType;
    @Size(max = 500)
    private String baseUrl;
    @Size(max = 4000)
    private String apiKey;
}
