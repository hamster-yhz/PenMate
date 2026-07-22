package com.penmate.backend.application.rag;

import com.penmate.backend.application.common.exception.BusinessErrorType;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.rag.model.ProjectAiConfiguration;
import com.penmate.backend.domain.rag.model.RagEmbeddingSpace;
import com.penmate.backend.domain.rag.model.RagRetrievalLog;
import com.penmate.backend.domain.rag.model.RagRetrievedChunk;
import com.penmate.backend.domain.rag.repository.ProjectAiConfigurationRepository;
import com.penmate.backend.domain.rag.repository.RagIndexRepository;
import com.penmate.backend.domain.rag.repository.RagRetrievalRepository;
import com.penmate.backend.domain.rag.service.EmbeddingGateway;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Service
public class RagRetrievalService {
    private final RagRetrievalRepository logs;
    private final RagIndexRepository indexes;
    private final ProjectAiConfigurationRepository configurations;
    private final NovelGateway novels;
    private final EmbeddingModelRoutingService routing;
    private final EmbeddingGateway embeddings;
    private final BusinessIdGenerator ids;
    private final JsonCodec jsonCodec;
    private final boolean legacyRetrieval;

    @Autowired
    public RagRetrievalService(RagRetrievalRepository logs, RagIndexRepository indexes,
                               ProjectAiConfigurationRepository configurations, NovelGateway novels,
                               EmbeddingModelRoutingService routing, EmbeddingGateway embeddings,
                               BusinessIdGenerator ids, JsonCodec jsonCodec) {
        this.logs = logs;
        this.indexes = indexes;
        this.configurations = configurations;
        this.novels = novels;
        this.routing = routing;
        this.embeddings = embeddings;
        this.ids = ids;
        this.jsonCodec = jsonCodec;
        this.legacyRetrieval = false;
    }

    RagRetrievalService(RagRetrievalRepository logs, BusinessIdGenerator ids, JsonCodec jsonCodec) {
        this.logs = logs;
        this.indexes = null;
        this.configurations = null;
        this.novels = null;
        this.routing = null;
        this.embeddings = null;
        this.ids = ids;
        this.jsonCodec = jsonCodec;
        this.legacyRetrieval = true;
    }

    public RetrievalResult retrieve(Long projectId, Long runId, String query, String traceId) {
        if (legacyRetrieval) {
            long startedAt = System.currentTimeMillis();
            return persist(projectId, runId, query, traceId, startedAt, logs.searchChunks(projectId, query, 3));
        }
        NovelProject project = novels.findProjectById(projectId);
        if (project == null) throw BusinessException.notFound("Novel project not found");
        return retrieve(projectId, project.getOwnerUserId(), runId, query, null, traceId);
    }

    public RetrievalResult retrieve(Long projectId, Long ownerUserId, Long runId, String query,
                                    List<String> sourceTypes, String traceId) {
        long startedAt = System.currentTimeMillis();
        if (query == null || query.isBlank()) throw BusinessException.badRequest("RAG query must not be blank");
        ProjectAiConfiguration configuration = configurations.findByProjectId(projectId);
        RagEmbeddingSpace space = indexes.findActiveSpaceForProject(projectId);
        if (configuration == null || configuration.getActiveIndexBuildId() == null
                || !"READY".equals(configuration.getIndexStatus())
                || space == null) {
            throw unavailable();
        }
        var model = routing.resolve(ownerUserId, configuration.getEmbeddingModelConfigId());
        List<float[]> response = embeddings.embed(new EmbeddingGateway.EmbeddingRequest(
                model.baseUrl(), model.apiKey(), model.modelName(), model.systemScope(), List.of(query.strip()),
                model.embeddingDimensions()));
        if (response.size() != 1 || response.getFirst().length != space.embeddingDimension()) {
            throw BusinessException.of("Query Embedding dimension does not match the active index");
        }
        List<RagRetrievedChunk> chunks = indexes.search(projectId, space, response.getFirst(), sourceTypes,
                configuration.getRetrievalCandidates(), configuration.getRetrievalTopK(),
                configuration.getRetrievalMaxPerSource(), configuration.getHnswEfSearch(),
                configuration.getSimilarityThreshold() == null ? null : configuration.getSimilarityThreshold().doubleValue());
        return persist(projectId, runId, query, traceId, startedAt, chunks);
    }

    public List<RagRetrievalLog> listRetrievalLogs(Long projectId) {
        return logs.listRetrievalLogs(projectId);
    }

    private RetrievalResult persist(Long projectId, Long runId, String query, String traceId,
                                    long startedAt, List<RagRetrievedChunk> chunks) {
        RagRetrievalLog log = new RagRetrievalLog();
        log.setRetrievalLogId(ids.nextId());
        log.setProjectId(projectId);
        log.setRunId(runId);
        log.setQueryText(query);
        log.setHitCount(chunks.size());
        log.setSourcesJson(sourcesJson(chunks));
        log.setLatencyMs((int) (System.currentTimeMillis() - startedAt));
        log.setAdopted(!chunks.isEmpty());
        log.setTraceId(traceId);
        logs.insertRetrievalLog(log);
        return new RetrievalResult(chunks, log.getId() == null ? log.getRetrievalLogId() : log.getId());
    }

    private String sourcesJson(List<RagRetrievedChunk> chunks) {
        try {
            return jsonCodec.write(chunks.stream().map(chunk -> new SourceItem(
                    chunk.getSourceType(), chunk.getSourceId(), chunk.getSourceTitle(), chunk.getChunkNo(), chunk.getDistance())).toList());
        } catch (RuntimeException exception) {
            return "[]";
        }
    }

    private BusinessException unavailable() {
        return BusinessException.of(BusinessErrorType.CONFLICT, "RAG_INDEX_UNAVAILABLE",
                "The project has no active vector index", null);
    }

    public record RetrievalResult(List<RagRetrievedChunk> chunks, Long logId) { }
    private record SourceItem(String sourceType, Long sourceId, String title, Integer chunkNo, Double distance) { }
}
