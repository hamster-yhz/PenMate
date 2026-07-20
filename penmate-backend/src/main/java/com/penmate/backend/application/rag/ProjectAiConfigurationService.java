package com.penmate.backend.application.rag;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.ops.AsyncJobQueueService;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.model.model.ModelUserPreferences;
import com.penmate.backend.domain.model.repository.ModelRepository;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.rag.model.ProjectAiConfiguration;
import com.penmate.backend.domain.rag.repository.ProjectAiConfigurationRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class ProjectAiConfigurationService {
    private final ProjectAiConfigurationRepository repository;
    private final ModelRepository models;
    private final NovelGateway novels;
    private final BusinessIdGenerator ids;
    private final AsyncJobQueueService jobs;

    public ProjectAiConfigurationService(ProjectAiConfigurationRepository repository, ModelRepository models,
                                         NovelGateway novels, BusinessIdGenerator ids, AsyncJobQueueService jobs) {
        this.repository = repository;
        this.models = models;
        this.novels = novels;
        this.ids = ids;
        this.jobs = jobs;
    }

    @Transactional
    public ProjectAiConfiguration initializeProject(Long projectId, Long ownerUserId) {
        if (repository.findByProjectId(projectId) != null) return repository.findByProjectId(projectId);
        ModelUserPreferences defaults = models.findUserPreferences(ownerUserId);
        ProjectAiConfiguration configuration = base(projectId);
        if (defaults != null) {
            configuration.setEmbeddingModelConfigId(defaults.getDefaultEmbeddingModelConfigId());
            configuration.setRouterModelConfigId(defaults.getDefaultRouterModelConfigId());
            configuration.setChunkTargetCharacters(value(defaults.getDefaultChunkTargetCharacters(), 800));
            configuration.setChunkOverlapCharacters(value(defaults.getDefaultChunkOverlapCharacters(), 120));
            configuration.setChunkMaxCharacters(value(defaults.getDefaultChunkMaxCharacters(), 1200));
            configuration.setStoryBibleRoutingMode(normalizeRouting(defaults.getDefaultStoryBibleRoutingMode()));
        }
        if (configuration.getEmbeddingModelConfigId() == null) {
            configuration.setStoryBibleRoutingMode("LLM_SELECTOR");
            configuration.setIndexStatus("UNBOUND");
        } else {
            configuration.setIndexStatus("REINDEX_REQUIRED");
            configuration.setActiveIndexBuildId(null);
        }
        validate(configuration, ownerUserId);
        if (repository.insert(configuration) != 1) throw BusinessException.of("Failed to initialize project AI configuration");
        return repository.findByProjectId(projectId);
    }

    public ProjectAiConfiguration get(Long projectId, Long actorUserId) {
        requireOwner(projectId, actorUserId);
        ProjectAiConfiguration result = repository.findByProjectId(projectId);
        if (result == null) throw BusinessException.notFound("Project AI configuration not found");
        return result;
    }

    @Transactional
    public ProjectAiConfiguration update(Long projectId, Long actorUserId, UpdateRequest request) {
        requireOwner(projectId, actorUserId);
        Objects.requireNonNull(request, "request");
        ProjectAiConfiguration current = repository.findByProjectIdForUpdate(projectId);
        if (current == null) throw BusinessException.notFound("Project AI configuration not found");
        if (repository.hasNonterminalRun(projectId)) {
            throw BusinessException.conflict("Project AI configuration cannot change while an Agent Run is active");
        }
        ProjectAiConfiguration next = copy(current);
        next.setEmbeddingModelConfigId(request.embeddingModelConfigId());
        next.setRouterModelConfigId(request.routerModelConfigId());
        next.setStoryBibleRoutingMode(normalizeRouting(request.storyBibleRoutingMode()));
        next.setChunkTargetCharacters(value(request.chunkTargetCharacters(), current.getChunkTargetCharacters()));
        next.setChunkOverlapCharacters(value(request.chunkOverlapCharacters(), current.getChunkOverlapCharacters()));
        next.setChunkMaxCharacters(value(request.chunkMaxCharacters(), current.getChunkMaxCharacters()));
        next.setRetrievalCandidates(value(request.retrievalCandidates(), current.getRetrievalCandidates()));
        next.setRetrievalTopK(value(request.retrievalTopK(), current.getRetrievalTopK()));
        next.setRetrievalMaxPerSource(value(request.retrievalMaxPerSource(), current.getRetrievalMaxPerSource()));
        next.setHnswEfSearch(value(request.hnswEfSearch(), current.getHnswEfSearch()));
        next.setSimilarityThreshold(request.similarityThreshold());
        boolean indexIdentityChanged = !Objects.equals(current.getEmbeddingModelConfigId(), next.getEmbeddingModelConfigId())
                || !Objects.equals(current.getChunkTargetCharacters(), next.getChunkTargetCharacters())
                || !Objects.equals(current.getChunkOverlapCharacters(), next.getChunkOverlapCharacters())
                || !Objects.equals(current.getChunkMaxCharacters(), next.getChunkMaxCharacters());
        if (next.getEmbeddingModelConfigId() == null) {
            next.setStoryBibleRoutingMode("LLM_SELECTOR");
            next.setIndexStatus("UNBOUND");
            next.setActiveIndexBuildId(null);
        } else if (indexIdentityChanged) {
            next.setStoryBibleRoutingMode("LLM_SELECTOR");
            next.setIndexStatus("REINDEX_REQUIRED");
            next.setActiveIndexBuildId(null);
            next.setLastErrorCode("PROJECT_AI_CONFIGURATION_CHANGED");
            next.setLastErrorMessage("Embedding or chunk configuration changed; rebuild the project index");
        }
        validate(next, actorUserId);
        if (repository.update(next) != 1) throw BusinessException.of("Failed to update project AI configuration");
        return repository.findByProjectId(projectId);
    }

    @Transactional
    public OpsAsyncJob requestRebuild(Long projectId, Long actorUserId) {
        requireOwner(projectId, actorUserId);
        ProjectAiConfiguration current = repository.findByProjectIdForUpdate(projectId);
        if (current == null) throw BusinessException.notFound("Project AI configuration not found");
        if (current.getEmbeddingModelConfigId() == null) {
            throw BusinessException.badRequest("Bind an Embedding model before rebuilding the project index");
        }
        if (repository.hasNonterminalRun(projectId)) {
            throw BusinessException.conflict("Project index cannot rebuild while an Agent Run is active");
        }
        current.setStoryBibleRoutingMode("LLM_SELECTOR");
        current.setIndexStatus("REINDEX_REQUIRED");
        current.setActiveIndexBuildId(null);
        current.setLastErrorCode(null);
        current.setLastErrorMessage(null);
        if (repository.update(current) != 1) throw BusinessException.of("Failed to prepare project index rebuild");
        long requestId = ids.nextId();
        String payload = "{\"projectId\":" + projectId
                + ",\"ownerUserId\":" + actorUserId
                + ",\"modelConfigId\":" + current.getEmbeddingModelConfigId()
                + ",\"requestId\":" + requestId + "}";
        return jobs.enqueue("RAG_REBUILD_PROJECT", "rag:project:" + projectId + ":rebuild:" + requestId,
                actorUserId, projectId, payload);
    }

    private ProjectAiConfiguration base(Long projectId) {
        ProjectAiConfiguration result = new ProjectAiConfiguration();
        result.setProjectAiConfigId(ids.nextId());
        result.setProjectId(projectId);
        result.setStoryBibleRoutingMode("LLM_SELECTOR");
        result.setChunkTargetCharacters(800);
        result.setChunkOverlapCharacters(120);
        result.setChunkMaxCharacters(1200);
        result.setRetrievalCandidates(30);
        result.setRetrievalTopK(8);
        result.setRetrievalMaxPerSource(3);
        result.setHnswEfSearch(100);
        result.setIndexStatus("UNBOUND");
        return result;
    }

    private void validate(ProjectAiConfiguration value, Long actor) {
        requireModel(actor, value.getEmbeddingModelConfigId(), "EMBEDDING", "Embedding model configuration is unavailable");
        requireModel(actor, value.getRouterModelConfigId(), "CHAT", "Router model configuration is unavailable");
        if (value.getEmbeddingModelConfigId() == null && !"LLM_SELECTOR".equals(value.getStoryBibleRoutingMode())) {
            throw BusinessException.badRequest("Projects without an Embedding model must use LLM_SELECTOR");
        }
        if (value.getChunkTargetCharacters() <= 0 || value.getChunkOverlapCharacters() < 0
                || value.getChunkOverlapCharacters() >= value.getChunkTargetCharacters()
                || value.getChunkMaxCharacters() < value.getChunkTargetCharacters()) {
            throw BusinessException.badRequest("Invalid chunk configuration");
        }
        if (value.getRetrievalTopK() <= 0 || value.getRetrievalCandidates() < value.getRetrievalTopK()
                || value.getRetrievalMaxPerSource() <= 0 || value.getHnswEfSearch() <= 0) {
            throw BusinessException.badRequest("Invalid retrieval configuration");
        }
    }

    private void requireModel(Long actor, Long id, String type, String message) {
        if (id != null && !models.existsAccessibleActiveConfiguration(actor, id, type)) throw BusinessException.badRequest(message);
    }

    private void requireOwner(Long projectId, Long actorUserId) {
        NovelProject project = novels.findProjectById(projectId);
        if (project == null || !Objects.equals(project.getOwnerUserId(), actorUserId)) {
            throw BusinessException.notFound("Novel project not found");
        }
    }

    private String normalizeRouting(String value) {
        String mode = value == null || value.isBlank() ? "LLM_SELECTOR" : value.trim().toUpperCase();
        if (!List.of("RETRIEVAL", "LLM_SELECTOR", "RETRIEVAL_THEN_LLM").contains(mode)) {
            throw BusinessException.badRequest("Unsupported Story Bible routing mode");
        }
        return mode;
    }

    private int value(Integer candidate, Integer fallback) { return candidate == null ? fallback : candidate; }

    private ProjectAiConfiguration copy(ProjectAiConfiguration source) {
        ProjectAiConfiguration result = new ProjectAiConfiguration();
        result.setProjectAiConfigId(source.getProjectAiConfigId());
        result.setProjectId(source.getProjectId());
        result.setEmbeddingModelConfigId(source.getEmbeddingModelConfigId());
        result.setStoryBibleRoutingMode(source.getStoryBibleRoutingMode());
        result.setRouterModelConfigId(source.getRouterModelConfigId());
        result.setChunkTargetCharacters(source.getChunkTargetCharacters());
        result.setChunkOverlapCharacters(source.getChunkOverlapCharacters());
        result.setChunkMaxCharacters(source.getChunkMaxCharacters());
        result.setRetrievalCandidates(source.getRetrievalCandidates());
        result.setRetrievalTopK(source.getRetrievalTopK());
        result.setRetrievalMaxPerSource(source.getRetrievalMaxPerSource());
        result.setHnswEfSearch(source.getHnswEfSearch());
        result.setSimilarityThreshold(source.getSimilarityThreshold());
        result.setIndexStatus(source.getIndexStatus());
        result.setActiveIndexBuildId(source.getActiveIndexBuildId());
        result.setLastErrorCode(source.getLastErrorCode());
        result.setLastErrorMessage(source.getLastErrorMessage());
        return result;
    }

    public record UpdateRequest(Long embeddingModelConfigId, String storyBibleRoutingMode,
                                Long routerModelConfigId, Integer chunkTargetCharacters,
                                Integer chunkOverlapCharacters, Integer chunkMaxCharacters,
                                Integer retrievalCandidates, Integer retrievalTopK,
                                Integer retrievalMaxPerSource, Integer hnswEfSearch,
                                BigDecimal similarityThreshold) {
    }
}
