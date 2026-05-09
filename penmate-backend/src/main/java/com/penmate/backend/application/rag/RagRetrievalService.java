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

    public List<RagRetrievalLog> listRetrievalLogs(Long projectId) {
        return ragRetrievalRepository.listRetrievalLogs(projectId);
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

