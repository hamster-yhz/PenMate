package com.penmate.backend.application.rag;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.ops.AsyncJobExecutionContext;
import com.penmate.backend.application.ops.AsyncJobQueueService;
import com.penmate.backend.domain.rag.model.ProjectAiConfiguration;
import com.penmate.backend.domain.rag.model.RagEmbeddingSpace;
import com.penmate.backend.domain.rag.model.RagSourceContent;
import com.penmate.backend.domain.rag.repository.ProjectAiConfigurationRepository;
import com.penmate.backend.domain.rag.repository.RagDocumentRepository;
import com.penmate.backend.domain.rag.repository.RagIndexRepository;
import com.penmate.backend.domain.rag.repository.RagSourceCatalogRepository;
import com.penmate.backend.domain.rag.service.DocumentChunker;
import com.penmate.backend.domain.rag.service.DocumentContentParser;
import com.penmate.backend.domain.rag.service.EmbeddingGateway;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RagIndexingService {
    private final ProjectAiConfigurationRepository configurations;
    private final RagSourceCatalogRepository sourceCatalog;
    private final RagIndexRepository indexes;
    private final RagDocumentRepository documents;
    private final EmbeddingModelRoutingService routing;
    private final EmbeddingGateway embeddings;
    private final ObjectStorageService storage;
    private final DocumentContentParser parser;
    private final BusinessIdGenerator ids;
    private final AsyncJobQueueService jobs;
    private final JsonCodec jsonCodec;
    private final DocumentChunker chunker;
    private final int batchSize;
    private final int batchMaxCharacters;
    private final int maxChunksPerProject;

    public RagIndexingService(ProjectAiConfigurationRepository configurations,
                              RagSourceCatalogRepository sourceCatalog, RagIndexRepository indexes,
                              RagDocumentRepository documents, EmbeddingModelRoutingService routing,
                              EmbeddingGateway embeddings, ObjectStorageService storage,
                              DocumentContentParser parser, BusinessIdGenerator ids,
                              AsyncJobQueueService jobs, JsonCodec jsonCodec,
                              RagIndexingSettings settings) {
        this.configurations = configurations;
        this.sourceCatalog = sourceCatalog;
        this.indexes = indexes;
        this.documents = documents;
        this.routing = routing;
        this.embeddings = embeddings;
        this.storage = storage;
        this.parser = parser;
        this.ids = ids;
        this.jobs = jobs;
        this.jsonCodec = jsonCodec;
        this.chunker = new DocumentChunker(settings.maxChunksPerSource());
        this.maxChunksPerProject = settings.maxChunksPerProject();
        this.batchSize = Math.min(32, settings.embeddingBatchSize());
        this.batchMaxCharacters = Math.min(30000, settings.embeddingBatchMaxCharacters());
    }

    public BuildResult rebuildProject(Long projectId, Long ownerUserId, AsyncJobExecutionContext context) {
        ProjectAiConfiguration configuration = requireBoundConfiguration(projectId);
        var model = routing.resolve(ownerUserId, configuration.getEmbeddingModelConfigId());
        List<RagSourceContent> sourceSnapshot = sourceCatalog.listProjectSources(projectId);
        List<PreparedSource> prepared = prepareSources(sourceSnapshot, configuration);
        List<PreparedChunk> allChunks = prepared.stream().flatMap(source -> source.chunks().stream()).toList();
        if (allChunks.isEmpty()) throw BusinessException.of("Project has no indexable content");
        if (allChunks.size() > maxChunksPerProject) throw BusinessException.of("Project exceeds the maximum active chunk count");

        Long buildId = null;
        try {
            List<Batch> batches = batches(allChunks);
            List<float[]> firstVectors = embed(model, batches.getFirst());
            int dimension = dimension(firstVectors);
            RagEmbeddingSpace space = ensureSpace(model, dimension);
            buildId = ids.nextId();
            long characters = allChunks.stream().mapToLong(chunk -> chunk.write().content().length()).sum();
            indexes.createBuild(buildId, projectId, model.modelConfigId(), space.embeddingSpaceId(), prepared.size(), characters);
            persistPrepared(buildId, projectId, space, prepared);
            int completed = 0;
            for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
                if (context != null && context.cancellationRequested()) throw new AsyncJobExecutionContext.JobCancelledException();
                Batch batch = batches.get(batchIndex);
                List<float[]> vectors = batchIndex == 0 ? firstVectors : embed(model, batch);
                validateDimension(vectors, dimension);
                indexes.insertVectors(space, buildId, projectId, vectorWrites(batch, vectors));
                completed += batch.chunks().size();
                if (context != null) context.heartbeat(completed, allChunks.size(), "Embedding project sources");
            }
            ensureSnapshotCurrent(projectId, sourceSnapshot);
            indexes.activateBuild(projectId, buildId, prepared.size(), allChunks.size());
            for (PreparedSource source : prepared) {
                if ("KNOWLEDGE_DOCUMENT".equals(source.source().sourceType())) {
                    documents.updateProcessingState(projectId, source.source().sourceId(), "DONE", "DONE", null, null);
                }
            }
            return new BuildResult(buildId, space.embeddingSpaceId(), prepared.size(), allChunks.size(), dimension);
        } catch (RuntimeException exception) {
            if (buildId != null) {
                indexes.failBuild(projectId, buildId, "RAG_BUILD_FAILED", message(exception));
                enqueueCleanup(ownerUserId, projectId, buildId);
            }
            throw exception;
        }
    }

    public void indexKnowledgeDocument(Long projectId, Long ownerUserId, Long documentId, long revision,
                                       AsyncJobExecutionContext context) {
        RagSourceContent source = sourceCatalog.findKnowledgeDocument(projectId, documentId);
        if (source == null || !Objects.equals(source.sourceRevision(), String.valueOf(revision))) return;
        ProjectAiConfiguration configuration = requireBoundConfiguration(projectId);
        var model = routing.resolve(ownerUserId, configuration.getEmbeddingModelConfigId());
        RagEmbeddingSpace space = indexes.findActiveSpaceForProject(projectId);
        if (space == null || configuration.getActiveIndexBuildId() == null) {
            enqueueRebuild(ownerUserId, projectId, model.modelConfigId(), revision);
            return;
        }
        PreparedSource prepared = prepareSource(source, configuration);
        if (prepared.chunks().isEmpty()) throw BusinessException.of("Document has no indexable content");
        Long sourceIndexId = prepared.sourceIndexId();
        indexes.resetStagedSource(configuration.getActiveIndexBuildId(), source.sourceType(), source.sourceId(), source.sourceRevision());
        indexes.insertSource(sourceIndexId, configuration.getActiveIndexBuildId(), projectId, source.sourceType(),
                source.sourceId(), source.sourceRevision(), source.title(), prepared.checksum(),
                prepared.characterCount(), prepared.chunks().size());
        indexes.insertChunks(sourceIndexId, configuration.getActiveIndexBuildId(), projectId, space.embeddingSpaceId(),
                source.sourceType(), source.sourceId(), prepared.chunks().stream().map(PreparedChunk::write).toList());
        List<Batch> batches = batches(prepared.chunks());
        int completed = 0;
        for (Batch batch : batches) {
            List<float[]> vectors = embed(model, batch);
            validateDimension(vectors, space.embeddingDimension());
            indexes.insertVectors(space, configuration.getActiveIndexBuildId(), projectId, vectorWrites(batch, vectors));
            completed += batch.chunks().size();
            if (context != null) context.heartbeat(completed, prepared.chunks().size(), "Embedding document");
        }
        RagSourceContent latest = sourceCatalog.findKnowledgeDocument(projectId, documentId);
        if (latest == null || !Objects.equals(latest.sourceRevision(), source.sourceRevision())) return;
        indexes.activateSource(configuration.getActiveIndexBuildId(), source.sourceType(), source.sourceId(), sourceIndexId);
        documents.updateProcessingState(projectId, documentId, "DONE", "DONE", null, null);
    }

    public void deleteKnowledgeDocument(Long projectId, Long documentId) {
        indexes.removeSource(projectId, "KNOWLEDGE_DOCUMENT", documentId);
    }

    private List<PreparedSource> prepareSources(List<RagSourceContent> sources, ProjectAiConfiguration configuration) {
        List<PreparedSource> result = new ArrayList<>();
        int chunks = 0;
        for (RagSourceContent source : sources) {
            PreparedSource prepared = prepareSource(source, configuration);
            chunks += prepared.chunks().size();
            if (chunks > maxChunksPerProject) throw BusinessException.of("Project exceeds the maximum active chunk count");
            if (!prepared.chunks().isEmpty()) result.add(prepared);
        }
        return List.copyOf(result);
    }

    private PreparedSource prepareSource(RagSourceContent source, ProjectAiConfiguration configuration) {
        String content = loadContent(source);
        int target = configuration.getChunkTargetCharacters();
        int overlap = configuration.getChunkOverlapCharacters();
        int hardMax = configuration.getChunkMaxCharacters();
        List<String> texts;
        if ("STORY_BIBLE_NODE".equals(source.sourceType()) && content.length() <= hardMax) texts = List.of(content);
        else texts = chunker.chunk(content, target, overlap, hardMax);
        Long sourceIndexId = ids.nextId();
        List<PreparedChunk> chunks = new ArrayList<>(texts.size());
        for (int index = 0; index < texts.size(); index++) {
            String value = texts.get(index);
            Long chunkId = ids.nextId();
            String metadata = json(Map.of("sourceType", source.sourceType(), "sourceId", String.valueOf(source.sourceId()),
                    "title", source.title(), "revision", source.sourceRevision()));
            chunks.add(new PreparedChunk(new RagIndexRepository.ChunkWrite(chunkId, index, value, sha256(value), metadata)));
        }
        return new PreparedSource(source, sourceIndexId, sha256(content), content.length(), List.copyOf(chunks));
    }

    private String loadContent(RagSourceContent source) {
        if (source.inlineContent() != null) return source.inlineContent().strip();
        byte[] bytes = storage.readBytes(source.objectKey());
        return parser.parse(source.fileExtension(), source.mimeType(), bytes).normalizedText();
    }

    private void persistPrepared(Long buildId, Long projectId, RagEmbeddingSpace space, List<PreparedSource> sources) {
        for (PreparedSource source : sources) {
            indexes.insertSource(source.sourceIndexId(), buildId, projectId, source.source().sourceType(),
                    source.source().sourceId(), source.source().sourceRevision(), source.source().title(),
                    source.checksum(), source.characterCount(), source.chunks().size());
            indexes.insertChunks(source.sourceIndexId(), buildId, projectId, space.embeddingSpaceId(),
                    source.source().sourceType(), source.source().sourceId(),
                    source.chunks().stream().map(PreparedChunk::write).toList());
        }
    }

    private List<Batch> batches(List<PreparedChunk> chunks) {
        List<Batch> result = new ArrayList<>();
        List<PreparedChunk> current = new ArrayList<>();
        int characters = 0;
        for (PreparedChunk chunk : chunks) {
            int length = chunk.write().content().length();
            if (!current.isEmpty() && (current.size() >= batchSize || characters + length > batchMaxCharacters)) {
                result.add(new Batch(List.copyOf(current)));
                current.clear();
                characters = 0;
            }
            current.add(chunk);
            characters += length;
        }
        if (!current.isEmpty()) result.add(new Batch(List.copyOf(current)));
        return List.copyOf(result);
    }

    private List<float[]> embed(EmbeddingModelRoutingService.EmbeddingExecutionConfig model, Batch batch) {
        return embeddings.embed(new EmbeddingGateway.EmbeddingRequest(model.baseUrl(), model.apiKey(), model.modelName(),
                model.systemScope(), batch.chunks().stream().map(chunk -> chunk.write().content()).toList(),
                model.embeddingDimensions()));
    }

    private List<RagIndexRepository.VectorWrite> vectorWrites(Batch batch, List<float[]> vectors) {
        if (batch.chunks().size() != vectors.size()) throw BusinessException.of("Embedding response count changed during indexing");
        List<RagIndexRepository.VectorWrite> result = new ArrayList<>(vectors.size());
        for (int index = 0; index < vectors.size(); index++) {
            result.add(new RagIndexRepository.VectorWrite(ids.nextId(), batch.chunks().get(index).write().chunkId(), vectors.get(index)));
        }
        return result;
    }

    private RagEmbeddingSpace ensureSpace(EmbeddingModelRoutingService.EmbeddingExecutionConfig model, int dimension) {
        if (dimension < 1 || dimension > 4000) throw BusinessException.of("Embedding dimension must be between 1 and 4000");
        String storageType = dimension <= 2000 ? "VECTOR" : "HALFVEC";
        String identity = sha256(String.join("\u001f", String.valueOf(model.providerId()), model.protocolCode(),
                model.baseUrl(), model.modelName(), String.valueOf(dimension), model.distanceMetric()));
        RagEmbeddingSpace existing = indexes.findSpace(identity);
        if (existing != null) return existing;
        long spaceId = ids.nextId();
        String partition = ("VECTOR".equals(storageType) ? "rag_vec_f32_" : "rag_vec_f16_") + spaceId;
        RagEmbeddingSpace created = new RagEmbeddingSpace(spaceId, identity, model.providerId(), model.protocolCode(),
                model.baseUrl(), model.modelName(), dimension, model.distanceMetric(), storageType, partition, "PROVISIONING");
        if (indexes.insertSpace(created) == 0) return Objects.requireNonNull(indexes.findSpace(identity));
        indexes.provisionSpace(created);
        return Objects.requireNonNull(indexes.findSpace(identity));
    }

    private void ensureSnapshotCurrent(Long projectId, List<RagSourceContent> original) {
        Map<String, String> expected = revisions(original);
        Map<String, String> actual = revisions(sourceCatalog.listProjectSources(projectId));
        if (!expected.equals(actual)) throw BusinessException.conflict("Project content changed during index rebuild");
    }

    private Map<String, String> revisions(List<RagSourceContent> sources) {
        Map<String, String> result = new LinkedHashMap<>();
        for (RagSourceContent source : sources) result.put(source.sourceType() + ":" + source.sourceId(), source.sourceRevision());
        return result;
    }

    private ProjectAiConfiguration requireBoundConfiguration(Long projectId) {
        ProjectAiConfiguration configuration = configurations.findByProjectId(projectId);
        if (configuration == null) {
            throw BusinessException.of("Project has no Embedding model configuration");
        }
        return configuration;
    }

    private int dimension(List<float[]> vectors) {
        if (vectors.isEmpty()) throw BusinessException.of("Embedding response is empty");
        int dimension = vectors.getFirst().length;
        validateDimension(vectors, dimension);
        return dimension;
    }

    private void validateDimension(List<float[]> vectors, int expected) {
        if (vectors.stream().anyMatch(vector -> vector.length != expected)) {
            throw BusinessException.of("Embedding dimension changed during indexing");
        }
    }

    private void enqueueRebuild(Long ownerUserId, Long projectId, Long modelConfigId, long revision) {
        jobs.enqueue("RAG_REBUILD_PROJECT", "rag:project:%d:rebuild:document:%d".formatted(projectId, revision),
                ownerUserId, projectId, json(Map.of("projectId", projectId, "ownerUserId", ownerUserId,
                        "modelConfigId", modelConfigId, "sourceRevision", revision)));
    }

    private void enqueueCleanup(Long ownerUserId, Long projectId, Long buildId) {
        jobs.enqueue("RAG_CLEANUP_EMBEDDING_SPACE", "rag:build:%d:cleanup".formatted(buildId), ownerUserId, projectId,
                json(Map.of("buildId", buildId)));
    }

    private String json(Object value) {
        return jsonCodec.write(value);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String message(RuntimeException exception) {
        String value = exception.getMessage();
        return value == null || value.isBlank() ? exception.getClass().getSimpleName() : value;
    }

    private record PreparedSource(RagSourceContent source, Long sourceIndexId, String checksum,
                                  long characterCount, List<PreparedChunk> chunks) { }
    private record PreparedChunk(RagIndexRepository.ChunkWrite write) { }
    private record Batch(List<PreparedChunk> chunks) { }
    public record BuildResult(Long buildId, Long embeddingSpaceId, int sourceCount, int chunkCount, int dimension) { }
}
