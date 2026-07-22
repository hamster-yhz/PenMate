package com.penmate.backend.application.rag.job;

import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.ops.AsyncJobExecutionContext;
import com.penmate.backend.application.ops.AsyncJobHandler;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import com.penmate.backend.domain.rag.repository.RagIndexRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RagCleanupEmbeddingSpaceJobHandler implements AsyncJobHandler {
    private final JsonCodec jsonCodec;
    private final RagIndexRepository indexes;

    public RagCleanupEmbeddingSpaceJobHandler(JsonCodec jsonCodec, RagIndexRepository indexes) {
        this.jsonCodec = jsonCodec;
        this.indexes = indexes;
    }

    @Override public String jobType() { return "RAG_CLEANUP_EMBEDDING_SPACE"; }

    @Override
    public String execute(OpsAsyncJob job, AsyncJobExecutionContext context) {
        Map<String, Object> payload = RagJobPayload.parse(jsonCodec, job);
        long buildId = RagJobPayload.requiredLong(payload, "buildId");
        indexes.deleteBuild(buildId);
        context.heartbeat(1, 1, "Staged index build removed");
        return jsonCodec.write(Map.of("cleanedBuildId", buildId));
    }
}
