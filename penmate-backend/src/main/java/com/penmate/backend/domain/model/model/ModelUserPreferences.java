package com.penmate.backend.domain.model.model;

import lombok.Data;

@Data
public class ModelUserPreferences {
    private Long userId;
    private Long defaultMainChatModelConfigId;
    private Long defaultWorkerChatModelConfigId;
    private Long defaultEmbeddingModelConfigId;
    private Long defaultRouterModelConfigId;
    private String defaultStoryBibleRoutingMode;
    private Integer defaultChunkTargetCharacters;
    private Integer defaultChunkOverlapCharacters;
    private Integer defaultChunkMaxCharacters;
}
