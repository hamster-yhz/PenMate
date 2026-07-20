package com.penmate.backend.application.rag.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.ops.AsyncJobExecutionContext;
import com.penmate.backend.application.ops.AsyncJobHandler;
import com.penmate.backend.application.rag.RagIndexingService;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import org.springframework.stereotype.Component;

@Component
public class RagReindexSourceJobHandler implements AsyncJobHandler {
    private final ObjectMapper mapper;
    private final RagIndexingService indexing;

    public RagReindexSourceJobHandler(ObjectMapper mapper, RagIndexingService indexing) {
        this.mapper = mapper;
        this.indexing = indexing;
    }

    @Override public String jobType() { return "RAG_REINDEX_SOURCE"; }

    @Override
    public String execute(OpsAsyncJob job, AsyncJobExecutionContext context) {
        JsonNode payload = RagJobPayload.parse(mapper, job);
        long projectId = RagJobPayload.requiredLong(payload, "projectId");
        long documentId = RagJobPayload.requiredLong(payload, "documentId");
        if ("DELETE".equals(payload.path("operation").asText())) {
            indexing.deleteKnowledgeDocument(projectId, documentId);
            return "{\"deletedSourceId\":" + documentId + "}";
        }
        long revision = RagJobPayload.requiredLong(payload, "sourceRevision");
        indexing.indexKnowledgeDocument(projectId, job.getOwnerUserId(), documentId, revision, context);
        return "{\"reindexedSourceId\":" + documentId + "}";
    }
}
