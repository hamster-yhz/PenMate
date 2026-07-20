package com.penmate.backend.application.rag.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.ops.AsyncJobExecutionContext;
import com.penmate.backend.application.ops.AsyncJobHandler;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.rag.repository.RagIndexRepository;
import org.springframework.stereotype.Component;

@Component
public class RagCleanupEmbeddingSpaceJobHandler implements AsyncJobHandler {
    private final ObjectMapper mapper;
    private final RagIndexRepository indexes;

    public RagCleanupEmbeddingSpaceJobHandler(ObjectMapper mapper, RagIndexRepository indexes) {
        this.mapper = mapper;
        this.indexes = indexes;
    }

    @Override public String jobType() { return "RAG_CLEANUP_EMBEDDING_SPACE"; }

    @Override
    public String execute(OpsAsyncJob job, AsyncJobExecutionContext context) {
        JsonNode payload = RagJobPayload.parse(mapper, job);
        long buildId = RagJobPayload.requiredLong(payload, "buildId");
        indexes.deleteBuild(buildId);
        context.heartbeat(1, 1, "Staged index build removed");
        return "{\"cleanedBuildId\":" + buildId + "}";
    }
}
