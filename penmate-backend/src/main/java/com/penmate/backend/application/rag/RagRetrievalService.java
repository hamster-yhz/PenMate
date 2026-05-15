package com.penmate.backend.application.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.rag.model.RagRetrievalLog;
import com.penmate.backend.domain.rag.model.RagRetrievedChunk;
import com.penmate.backend.domain.rag.repository.RagRetrievalRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RagRetrievalService {

    private final RagRetrievalRepository ragRetrievalRepository;
    private final BusinessIdGenerator businessIdGenerator;
    private final ObjectMapper objectMapper;

    public RagRetrievalService(RagRetrievalRepository ragRetrievalRepository,
                               BusinessIdGenerator businessIdGenerator,
                               ObjectMapper objectMapper) {
        this.ragRetrievalRepository = ragRetrievalRepository;
        this.businessIdGenerator = businessIdGenerator;
        this.objectMapper = objectMapper;
    }

    public RetrievalResult retrieve(Long projectId, Long taskId, String query, String traceId) {
        long startAt = System.currentTimeMillis();
        List<RagRetrievedChunk> chunks = ragRetrievalRepository.searchChunks(projectId, query, 3);
        return persistRetrievalLog(projectId, taskId, query, traceId, startAt, chunks);
    }

    public RetrievalResult retrieve(HybridRagQuery query, String traceId) {
        long startAt = System.currentTimeMillis();
        List<RagRetrievedChunk> chunks = ragRetrievalRepository.searchChunks(
                query.projectId(),
                query.queryText(),
                query.topK(),
                query.chapterId(),
                query.storyBibleVersion(),
                joinEntities(query.userMentionedEntities()),
                joinCsv(query.activatedSkills()),
                joinCsv(query.intentTags()),
                query.searchScope() == null ? null : query.searchScope().name()
        );
        return persistRetrievalLog(query.projectId(), query.taskId(), query.queryText(), traceId, startAt, chunks);
    }

    public List<RagRetrievalLog> listRetrievalLogs(Long projectId) {
        return ragRetrievalRepository.listRetrievalLogs(projectId);
    }

    private RetrievalResult persistRetrievalLog(Long projectId,
                                                Long taskId,
                                                String query,
                                                String traceId,
                                                long startAt,
                                                List<RagRetrievedChunk> chunks) {
        RagRetrievalLog retrievalLog = new RagRetrievalLog();
        retrievalLog.setRetrievalLogId(businessIdGenerator.nextId());
        retrievalLog.setProjectId(projectId);
        retrievalLog.setTaskId(taskId);
        retrievalLog.setQueryText(query);
        retrievalLog.setHitCount(chunks.size());
        retrievalLog.setSourcesJson(toSourcesJson(chunks));
        retrievalLog.setLatencyMs((int) (System.currentTimeMillis() - startAt));
        retrievalLog.setAdopted(!chunks.isEmpty());
        retrievalLog.setTraceId(traceId);
        ragRetrievalRepository.insertRetrievalLog(retrievalLog);
        return new RetrievalResult(chunks, retrievalLog.getId());
    }

    private String joinEntities(List<String> entities) {
        return joinValues(entities, "|");
    }

    private String joinCsv(List<String> values) {
        return joinValues(values, ",");
    }

    private String joinValues(List<String> values, String delimiter) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .reduce((left, right) -> left + delimiter + right)
                .orElse(null);
    }

    private String toSourcesJson(List<RagRetrievedChunk> chunks) {
        try {
            return objectMapper.writeValueAsString(chunks.stream()
                    .map(chunk -> new SourceItem(chunk.getDocumentId(), chunk.getDocumentTitle(), chunk.getChunkNo()))
                    .toList());
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    public record RetrievalResult(List<RagRetrievedChunk> chunks, Long logId) {
    }

    private record SourceItem(Long documentId, String documentTitle, Integer chunkNo) {
    }
}

