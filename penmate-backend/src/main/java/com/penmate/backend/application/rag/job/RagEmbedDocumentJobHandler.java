package com.penmate.backend.application.rag.job;

import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.ops.AsyncJobExecutionContext;
import com.penmate.backend.application.ops.AsyncJobHandler;
import com.penmate.backend.application.rag.RagApplicationService;
import com.penmate.backend.application.rag.RagIndexingService;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RagEmbedDocumentJobHandler implements AsyncJobHandler {
    private final JsonCodec jsonCodec;
    private final RagIndexingService indexing;
    private final RagApplicationService rag;

    public RagEmbedDocumentJobHandler(JsonCodec jsonCodec, RagIndexingService indexing, RagApplicationService rag) {
        this.jsonCodec = jsonCodec;
        this.indexing = indexing;
        this.rag = rag;
    }

    @Override public String jobType() { return "RAG_EMBED_DOCUMENT"; }

    @Override
    public String execute(OpsAsyncJob job, AsyncJobExecutionContext context) {
        Map<String, Object> payload = RagJobPayload.parse(jsonCodec, job);
        long projectId = RagJobPayload.requiredLong(payload, "projectId");
        long documentId = RagJobPayload.requiredLong(payload, "documentId");
        long revision = RagJobPayload.requiredLong(payload, "sourceRevision");
        try {
            indexing.indexKnowledgeDocument(projectId, job.getOwnerUserId(), documentId, revision, context);
            return jsonCodec.write(Map.of("documentId", documentId, "indexStatus", "DONE"));
        } catch (RuntimeException exception) {
            rag.updateProcessingState(projectId, documentId, "DONE", "FAILED", "RAG_EMBED_FAILED", exception.getMessage());
            throw exception;
        }
    }
}
