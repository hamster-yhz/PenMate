package com.penmate.backend.application.rag.job;

import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.ops.AsyncJobExecutionContext;
import com.penmate.backend.application.ops.AsyncJobHandler;
import com.penmate.backend.application.rag.RagIndexingService;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RagReindexSourceJobHandler implements AsyncJobHandler {
    private final JsonCodec jsonCodec;
    private final RagIndexingService indexing;

    public RagReindexSourceJobHandler(JsonCodec jsonCodec, RagIndexingService indexing) {
        this.jsonCodec = jsonCodec;
        this.indexing = indexing;
    }

    @Override public String jobType() { return "RAG_REINDEX_SOURCE"; }

    @Override
    public String execute(OpsAsyncJob job, AsyncJobExecutionContext context) {
        Map<String, Object> payload = RagJobPayload.parse(jsonCodec, job);
        long projectId = RagJobPayload.requiredLong(payload, "projectId");
        long documentId = RagJobPayload.requiredLong(payload, "documentId");
        if ("DELETE".equals(RagJobPayload.text(payload, "operation"))) {
            indexing.deleteKnowledgeDocument(projectId, documentId);
            return jsonCodec.write(Map.of("deletedSourceId", documentId));
        }
        long revision = RagJobPayload.requiredLong(payload, "sourceRevision");
        indexing.indexKnowledgeDocument(projectId, job.getOwnerUserId(), documentId, revision, context);
        return jsonCodec.write(Map.of("reindexedSourceId", documentId));
    }
}
