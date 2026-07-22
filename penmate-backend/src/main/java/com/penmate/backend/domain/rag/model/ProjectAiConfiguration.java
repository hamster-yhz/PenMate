package com.penmate.backend.domain.rag.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class ProjectAiConfiguration {
    private Long projectAiConfigId;
    private Long projectId;
    private Long creativeModelConfigId;
    private Long embeddingModelConfigId;
    private String storyBibleRoutingMode;
    private Long routerModelConfigId;
    private Integer chunkTargetCharacters;
    private Integer chunkOverlapCharacters;
    private Integer chunkMaxCharacters;
    private Integer retrievalCandidates;
    private Integer retrievalTopK;
    private Integer retrievalMaxPerSource;
    private Integer hnswEfSearch;
    private BigDecimal similarityThreshold;
    private String indexStatus;
    private Long activeIndexBuildId;
    private String lastErrorCode;
    private String lastErrorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}
