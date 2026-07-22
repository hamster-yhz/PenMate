package com.penmate.backend.application.rag.job;

import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.ops.AsyncJobExecutionContext;
import com.penmate.backend.application.ops.AsyncJobHandler;
import com.penmate.backend.application.rag.RagIndexingService;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RagRebuildProjectJobHandler implements AsyncJobHandler {
    private final JsonCodec jsonCodec;
    private final RagIndexingService indexing;

    public RagRebuildProjectJobHandler(JsonCodec jsonCodec, RagIndexingService indexing) {
        this.jsonCodec = jsonCodec;
        this.indexing = indexing;
    }

    @Override public String jobType() { return "RAG_REBUILD_PROJECT"; }

    @Override
    public String execute(OpsAsyncJob job, AsyncJobExecutionContext context) throws Exception {
        Map<String, Object> payload = RagJobPayload.parse(jsonCodec, job);
        long projectId = RagJobPayload.requiredLong(payload, "projectId");
        long ownerUserId = RagJobPayload.longOrDefault(payload, "ownerUserId", job.getOwnerUserId());
        return jsonCodec.write(indexing.rebuildProject(projectId, ownerUserId, context));
    }
}
