package com.penmate.backend.interfaces.api.rag.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateProjectAiConfigurationDto {
    private String creativeModelConfigId;
    private String embeddingModelConfigId;
    private String storyBibleRoutingMode;
    private String routerModelConfigId;
    private Integer chunkTargetCharacters;
    private Integer chunkOverlapCharacters;
    private Integer chunkMaxCharacters;
    private Integer retrievalCandidates;
    private Integer retrievalTopK;
    private Integer retrievalMaxPerSource;
    private Integer hnswEfSearch;
    private BigDecimal similarityThreshold;
}
