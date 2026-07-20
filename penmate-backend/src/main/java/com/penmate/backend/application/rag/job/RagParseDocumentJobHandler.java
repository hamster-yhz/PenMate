package com.penmate.backend.application.rag.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.ops.AsyncJobExecutionContext;
import com.penmate.backend.application.ops.AsyncJobHandler;
import com.penmate.backend.application.ops.AsyncJobQueueService;
import com.penmate.backend.application.rag.RagApplicationService;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import org.springframework.stereotype.Component;

@Component
public class RagParseDocumentJobHandler implements AsyncJobHandler {
    private final ObjectMapper mapper;
    private final RagApplicationService rag;
    private final AsyncJobQueueService jobs;

    public RagParseDocumentJobHandler(ObjectMapper mapper, RagApplicationService rag, AsyncJobQueueService jobs) {
        this.mapper = mapper;
        this.rag = rag;
        this.jobs = jobs;
    }

    @Override public String jobType() { return "RAG_PARSE_DOCUMENT"; }

    @Override
    public String execute(OpsAsyncJob job, AsyncJobExecutionContext context) {
        JsonNode payload = RagJobPayload.parse(mapper, job);
        long projectId = RagJobPayload.requiredLong(payload, "projectId");
        long documentId = RagJobPayload.requiredLong(payload, "documentId");
        long revision = RagJobPayload.requiredLong(payload, "sourceRevision");
        try {
            rag.loadAndParse(projectId, documentId, revision);
            rag.updateProcessingState(projectId, documentId, "DONE", "PENDING", null, null);
            jobs.enqueue("RAG_EMBED_DOCUMENT", "rag:document:%d:embed:%d".formatted(documentId, revision),
                    job.getOwnerUserId(), projectId, job.getPayloadJson());
            context.heartbeat(1, 1, "Document parsed");
            return "{\"documentId\":" + documentId + ",\"parseStatus\":\"DONE\"}";
        } catch (RuntimeException exception) {
            rag.updateProcessingState(projectId, documentId, "FAILED", "FAILED", "RAG_PARSE_FAILED", exception.getMessage());
            throw exception;
        }
    }
}
