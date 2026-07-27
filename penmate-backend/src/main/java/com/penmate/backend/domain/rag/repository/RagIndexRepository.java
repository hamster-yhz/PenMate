package com.penmate.backend.domain.rag.repository;

import com.penmate.backend.domain.rag.model.RagEmbeddingSpace;
import com.penmate.backend.domain.rag.model.RagRetrievedChunk;

import java.util.List;

public interface RagIndexRepository {
    RagEmbeddingSpace findSpace(String identityHash);
    RagEmbeddingSpace findActiveSpaceForProject(Long projectId);
    int insertSpace(RagEmbeddingSpace space);
    void provisionSpace(RagEmbeddingSpace space);
    Long createBuild(Long buildId, Long projectId, Long modelConfigId, Long embeddingSpaceId, int sourceCount, long characterCount);
    Long insertSource(Long sourceIndexId, Long buildId, Long projectId, String sourceType, Long sourceId,
                      String revision, String title, String checksum, long characterCount, int chunkCount);
    void resetStagedSource(Long buildId, String sourceType, Long sourceId, String revision);
    boolean isSourceRevisionActive(Long buildId, String sourceType, Long sourceId, String revision);
    void insertChunks(Long sourceIndexId, Long buildId, Long projectId, Long embeddingSpaceId,
                      String sourceType, Long sourceId, List<ChunkWrite> chunks);
    void insertVectors(RagEmbeddingSpace space, Long buildId, Long projectId, List<VectorWrite> vectors);
    void activateBuild(Long projectId, Long buildId, int sourceCount, int chunkCount);
    List<Long> findSupersededBuildIds(Long projectId);
    List<BuildCleanupCandidate> findSupersededBuilds();
    void failBuild(Long projectId, Long buildId, String errorCode, String errorMessage);
    void failProjectBuild(Long projectId, Long modelConfigId, String errorCode, String errorMessage);
    void activateSource(Long buildId, String sourceType, Long sourceId, Long sourceIndexId);
    void removeSource(Long projectId, String sourceType, Long sourceId);
    void deleteBuild(Long buildId);
    List<RagRetrievedChunk> search(Long projectId, RagEmbeddingSpace space, float[] queryVector,
                                   List<String> sourceTypes, int candidates, int topK,
                                   int maxPerSource, int efSearch, Double threshold);

    record ChunkWrite(Long chunkId, int chunkNo, String content, String contentHash, String metadataJson) {
    }
    record VectorWrite(Long vectorId, Long chunkId, float[] embedding) {
    }
    record BuildCleanupCandidate(Long buildId, Long projectId, Long ownerUserId) {
    }
}
