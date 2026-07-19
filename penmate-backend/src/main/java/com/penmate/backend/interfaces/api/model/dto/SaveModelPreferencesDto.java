package com.penmate.backend.interfaces.api.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class SaveModelPreferencesDto {
    @Pattern(regexp = "^$|^[1-9]\\d*$") @JsonDeserialize(using = StringIdOnlyDeserializer.class)
    private String defaultMainChatModelConfigId;
    @Pattern(regexp = "^$|^[1-9]\\d*$") @JsonDeserialize(using = StringIdOnlyDeserializer.class)
    private String defaultWorkerChatModelConfigId;
    @Pattern(regexp = "^$|^[1-9]\\d*$") @JsonDeserialize(using = StringIdOnlyDeserializer.class)
    private String defaultEmbeddingModelConfigId;
    @Pattern(regexp = "^$|^[1-9]\\d*$") @JsonDeserialize(using = StringIdOnlyDeserializer.class)
    private String defaultRouterModelConfigId;
    @Pattern(regexp = "RETRIEVAL|LLM_SELECTOR|RETRIEVAL_THEN_LLM")
    private String defaultStoryBibleRoutingMode;
    @Positive private Integer defaultChunkTargetCharacters;
    @PositiveOrZero private Integer defaultChunkOverlapCharacters;
    @Positive private Integer defaultChunkMaxCharacters;
}
