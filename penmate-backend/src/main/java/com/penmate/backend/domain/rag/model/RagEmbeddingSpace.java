package com.penmate.backend.domain.rag.model;

public record RagEmbeddingSpace(Long embeddingSpaceId, String identityHash, Long providerId,
                                String protocolCode, String normalizedBaseUrl, String modelName,
                                int embeddingDimension, String distanceMetric, String storageType,
                                String partitionName, String spaceStatus) {
}
