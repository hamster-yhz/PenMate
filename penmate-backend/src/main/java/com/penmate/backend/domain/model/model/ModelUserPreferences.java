package com.penmate.backend.domain.model.model;

import lombok.Data;

@Data
public class ModelUserPreferences {
    private Long userId;
    private Long defaultCreativeModelConfigId;
    private Long defaultContextSelectorModelConfigId;
    private Long defaultEmbeddingModelConfigId;
    private String defaultStoryBibleRoutingMode;
    private Integer defaultChunkTargetCharacters;
    private Integer defaultChunkOverlapCharacters;
    private Integer defaultChunkMaxCharacters;
}
