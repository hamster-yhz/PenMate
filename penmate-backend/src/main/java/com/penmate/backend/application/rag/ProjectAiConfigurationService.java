package com.penmate.backend.application.rag;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.ops.AsyncJobQueueService;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.ops.repository.OpsRepository;
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
    private final OpsRepository ops;

    public ProjectAiConfigurationService(ProjectAiConfigurationRepository repository, ModelRepository models,
                                         NovelGateway novels, BusinessIdGenerator ids, AsyncJobQueueService jobs,
                                         OpsRepository ops) {
        this.repository = repository;
        this.models = models;
        this.novels = novels;
        this.ids = ids;
        this.jobs = jobs;
        this.ops = ops;
    }

    @Transactional
    public ProjectAiConfiguration initializeProject(Long projectId, Long ownerUserId) {
        ProjectAiConfiguration existing = repository.findByProjectId(projectId);
        if (existing != null) return hydrate(existing);
        ModelUserPreferences defaults = models.findUserPreferences(ownerUserId);
        ProjectAiConfiguration configuration = base(projectId);
        if (defaults != null) {
            configuration.setCreativeModelConfigId(defaults.getDefaultCreativeModelConfigId());
            configuration.setEmbeddingModelConfigId(defaults.getDefaultEmbeddingModelConfigId());
            configuration.setRouterModelConfigId(defaults.getDefaultContextSelectorModelConfigId());
            configuration.setChunkTargetCharacters(value(defaults.getDefaultChunkTargetCharacters(), 800));
            configuration.setChunkOverlapCharacters(value(defaults.getDefaultChunkOverlapCharacters(), 120));
            configuration.setChunkMaxCharacters(value(defaults.getDefaultChunkMaxCharacters(), 1200));
        }
        configuration.setStoryBibleRoutingMode("AGENT_DRIVEN");
        configuration.setRagEnabled(false);
        if (defaults == null || defaults.getDefaultEmbeddingModelConfigId() == null) {
            configuration.setIndexStatus("UNBOUND");
        } else {
            configuration.setIndexStatus("REINDEX_REQUIRED");
            configuration.setActiveIndexBuildId(null);
        }
        validate(configuration, ownerUserId, configuration.getEmbeddingModelConfigId());
        if (repository.insert(configuration) != 1) throw BusinessException.of("Failed to initialize project AI configuration");
        return hydrate(repository.findByProjectId(projectId));
    }

    public ProjectAiConfiguration get(Long projectId, Long actorUserId) {
        requireOwner(projectId, actorUserId);
        ProjectAiConfiguration result = repository.findByProjectId(projectId);
        if (result == null) throw BusinessException.notFound("Project AI configuration not found");
        return hydrate(result);
    }

    @Transactional
    public ProjectAiConfiguration update(Long projectId, Long actorUserId, UpdateRequest request) {
        requireOwner(projectId, actorUserId);
        Objects.requireNonNull(request, "request");
        ProjectAiConfiguration current = repository.findByProjectIdForUpdate(projectId);
        if (current == null) throw BusinessException.notFound("Project AI configuration not found");
        if ("QUEUED".equals(current.getIndexStatus()) || "BUILDING".equals(current.getIndexStatus())) {
            throw BusinessException.conflict("Wait for the current index rebuild before changing project AI configuration");
        }
        ProjectAiConfiguration next = copy(current);
        next.setCreativeModelConfigId(request.creativeModelConfigId());
        next.setEmbeddingModelConfigId(request.embeddingModelConfigId());
        next.setRagEnabled(request.ragEnabled() == null ? current.getRagEnabled() : request.ragEnabled());
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
        Long currentEffectiveEmbedding = current.getEmbeddingModelConfigId();
        Long nextEffectiveEmbedding = next.getEmbeddingModelConfigId();
        boolean indexIdentityChanged = !Objects.equals(currentEffectiveEmbedding, nextEffectiveEmbedding)
                || !Objects.equals(current.getChunkTargetCharacters(), next.getChunkTargetCharacters())
                || !Objects.equals(current.getChunkOverlapCharacters(), next.getChunkOverlapCharacters())
                || !Objects.equals(current.getChunkMaxCharacters(), next.getChunkMaxCharacters());
        if (nextEffectiveEmbedding == null) {
            next.setIndexStatus("UNBOUND");
            next.setActiveIndexBuildId(null);
        } else if (indexIdentityChanged) {
            next.setIndexStatus("REINDEX_REQUIRED");
            next.setActiveIndexBuildId(null);
            next.setLastErrorCode("PROJECT_AI_CONFIGURATION_CHANGED");
            next.setLastErrorMessage("Embedding or chunk configuration changed; rebuild the project index");
        }
        validate(next, actorUserId, nextEffectiveEmbedding);
        if (repository.update(next) != 1) throw BusinessException.of("Failed to update project AI configuration");
        return hydrate(repository.findByProjectId(projectId));
    }

    @Transactional
    public OpsAsyncJob requestRebuild(Long projectId, Long actorUserId) {
        requireOwner(projectId, actorUserId);
        ProjectAiConfiguration current = repository.findByProjectIdForUpdate(projectId);
        if (current == null) throw BusinessException.notFound("Project AI configuration not found");
        Long effectiveEmbeddingModelConfigId = current.getEmbeddingModelConfigId();
        if (!Boolean.TRUE.equals(current.getRagEnabled())) {
            throw BusinessException.badRequest("Enable RAG before rebuilding the project index");
        }
        if (effectiveEmbeddingModelConfigId == null) {
            throw BusinessException.badRequest("Bind an Embedding model before rebuilding the project index");
        }
        if ("QUEUED".equals(current.getIndexStatus()) || "BUILDING".equals(current.getIndexStatus())) {
            throw BusinessException.conflict("The project index is already being rebuilt");
        }
        current.setIndexStatus("QUEUED");
        current.setActiveIndexBuildId(null);
        current.setLastErrorCode(null);
        current.setLastErrorMessage(null);
        if (repository.update(current) != 1) throw BusinessException.of("Failed to prepare project index rebuild");
        long requestId = ids.nextId();
        String payload = "{\"projectId\":" + projectId
                + ",\"ownerUserId\":" + actorUserId
                + ",\"modelConfigId\":" + effectiveEmbeddingModelConfigId
                + ",\"requestId\":" + requestId + "}";
        return jobs.enqueue("RAG_REBUILD_PROJECT", "rag:project:" + projectId + ":rebuild:" + requestId,
                actorUserId, projectId, payload);
    }

    @Transactional
    public OpsAsyncJob cancelRebuild(Long projectId, Long actorUserId, Long jobId) {
        requireOwner(projectId, actorUserId);
        ProjectAiConfiguration configuration = repository.findByProjectIdForUpdate(projectId);
        if (configuration == null) throw BusinessException.notFound("Project AI configuration not found");
        OpsAsyncJob job = ops.findJobById(jobId);
        if (job == null || !Objects.equals(projectId, job.getProjectId())
                || !Objects.equals(actorUserId, job.getOwnerUserId())
                || !"RAG_REBUILD_PROJECT".equals(job.getJobType())) {
            throw BusinessException.notFound("Project index rebuild job not found");
        }
        if (job.terminal()) {
            if ("CANCELLED".equals(job.getStatus())) return job;
            throw BusinessException.conflict("Project index rebuild job has already finished");
        }
        jobs.requestCancel(jobId);
        configuration.setIndexStatus("REINDEX_REQUIRED");
        configuration.setActiveIndexBuildId(null);
        configuration.setLastErrorCode(null);
        configuration.setLastErrorMessage(null);
        if (repository.update(configuration) != 1) {
            throw BusinessException.of("Failed to update project index status after cancellation");
        }
        return ops.findJobById(jobId);
    }

    public RebuildState rebuildState(ProjectAiConfiguration configuration) {
        if (configuration == null || configuration.getProjectId() == null) return null;
        boolean cancellationState = "REINDEX_REQUIRED".equals(configuration.getIndexStatus());
        if (!cancellationState && !List.of("QUEUED", "BUILDING", "FAILED").contains(configuration.getIndexStatus())) return null;
        OpsAsyncJob job = ops.findLatestProjectJob(configuration.getProjectId(), "RAG_REBUILD_PROJECT");
        if (job == null) return null;
        if (cancellationState && !"CANCELLED".equals(job.getStatus()) && !job.cancellationRequested()) return null;
        String status = switch (job.getStatus()) {
            case "RUNNING" -> job.cancellationRequested() ? "CANCELLING" : "BUILDING";
            case "QUEUED", "RETRY_WAIT" -> "QUEUED";
            case "FAILED" -> "FAILED";
            case "CANCELLED" -> "CANCELLED";
            default -> configuration.getIndexStatus();
        };
        return new RebuildState(job.getJobId(), status, job.getProgressCurrent(), job.getProgressTotal(),
                job.getProgressMessage(), job.getLastErrorMessage());
    }

    private ProjectAiConfiguration base(Long projectId) {
        ProjectAiConfiguration result = new ProjectAiConfiguration();
        result.setProjectAiConfigId(ids.nextId());
        result.setProjectId(projectId);
        result.setStoryBibleRoutingMode("AGENT_DRIVEN");
        result.setRagEnabled(false);
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

    private void validate(ProjectAiConfiguration value, Long actor, Long effectiveEmbeddingModelConfigId) {
        requireModel(actor, value.getCreativeModelConfigId(), "CHAT", "Creative model configuration is unavailable");
        requireModel(actor, effectiveEmbeddingModelConfigId, "EMBEDDING", "Embedding model configuration is unavailable");
        requireModel(actor, value.getRouterModelConfigId(), "CHAT", "Router model configuration is unavailable");
        if (requiresEmbedding(value.getStoryBibleRoutingMode())
                && (!Boolean.TRUE.equals(value.getRagEnabled()) || effectiveEmbeddingModelConfigId == null)) {
            throw BusinessException.badRequest("Retrieval routing requires enabled RAG and an Embedding model");
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
        String mode = value == null || value.isBlank() ? "AGENT_DRIVEN" : value.trim().toUpperCase();
        if (!List.of("AGENT_DRIVEN", "RETRIEVAL", "LLM_SELECTOR", "RETRIEVAL_THEN_LLM").contains(mode)) {
            throw BusinessException.badRequest("Unsupported Story Bible routing mode");
        }
        return mode;
    }

    private boolean requiresEmbedding(String mode) {
        return "RETRIEVAL".equals(mode) || "RETRIEVAL_THEN_LLM".equals(mode);
    }

    private int value(Integer candidate, Integer fallback) { return candidate == null ? fallback : candidate; }

    private ProjectAiConfiguration hydrate(ProjectAiConfiguration configuration) {
        if (configuration != null) {
            configuration.setLastIndexCompletedAt(repository.findLastCompletedAt(configuration.getProjectId()));
        }
        return configuration;
    }

    private ProjectAiConfiguration copy(ProjectAiConfiguration source) {
        ProjectAiConfiguration result = new ProjectAiConfiguration();
        result.setProjectAiConfigId(source.getProjectAiConfigId());
        result.setProjectId(source.getProjectId());
        result.setCreativeModelConfigId(source.getCreativeModelConfigId());
        result.setEmbeddingModelConfigId(source.getEmbeddingModelConfigId());
        result.setRagEnabled(source.getRagEnabled());
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

    public record UpdateRequest(Long creativeModelConfigId, Long embeddingModelConfigId, String storyBibleRoutingMode,
                                Long routerModelConfigId, Integer chunkTargetCharacters,
                                Integer chunkOverlapCharacters, Integer chunkMaxCharacters,
                                Integer retrievalCandidates, Integer retrievalTopK,
                                Integer retrievalMaxPerSource, Integer hnswEfSearch,
                                BigDecimal similarityThreshold, Boolean ragEnabled) {
        public UpdateRequest(Long creativeModelConfigId, Long embeddingModelConfigId, String storyBibleRoutingMode,
                             Long routerModelConfigId, Integer chunkTargetCharacters,
                             Integer chunkOverlapCharacters, Integer chunkMaxCharacters,
                             Integer retrievalCandidates, Integer retrievalTopK,
                             Integer retrievalMaxPerSource, Integer hnswEfSearch,
                             BigDecimal similarityThreshold) {
            this(creativeModelConfigId, embeddingModelConfigId, storyBibleRoutingMode, routerModelConfigId,
                    chunkTargetCharacters, chunkOverlapCharacters, chunkMaxCharacters,
                    retrievalCandidates, retrievalTopK, retrievalMaxPerSource, hnswEfSearch,
                    similarityThreshold, null);
        }

        public UpdateRequest(Long embeddingModelConfigId, String storyBibleRoutingMode,
                             Long routerModelConfigId, Integer chunkTargetCharacters,
                             Integer chunkOverlapCharacters, Integer chunkMaxCharacters,
                             Integer retrievalCandidates, Integer retrievalTopK,
                             Integer retrievalMaxPerSource, Integer hnswEfSearch,
                             BigDecimal similarityThreshold) {
            this(null, embeddingModelConfigId, storyBibleRoutingMode, routerModelConfigId,
                    chunkTargetCharacters, chunkOverlapCharacters, chunkMaxCharacters,
                    retrievalCandidates, retrievalTopK, retrievalMaxPerSource, hnswEfSearch,
                    similarityThreshold, null);
        }
    }

    public record RebuildState(Long jobId, String status, Long progressCurrent, Long progressTotal,
                               String progressMessage, String errorMessage) {
    }
}
