package com.penmate.backend.application.rag.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.ops.AsyncJobExecutionContext;
import com.penmate.backend.application.ops.AsyncJobHandler;
import com.penmate.backend.application.rag.RagIndexingService;
import com.penmate.backend.domain.ops.model.OpsAsyncJob;
import org.springframework.stereotype.Component;

@Component
public class RagRebuildProjectJobHandler implements AsyncJobHandler {
    private final ObjectMapper mapper;
    private final RagIndexingService indexing;

    public RagRebuildProjectJobHandler(ObjectMapper mapper, RagIndexingService indexing) {
        this.mapper = mapper;
        this.indexing = indexing;
    }

    @Override public String jobType() { return "RAG_REBUILD_PROJECT"; }

    @Override
    public String execute(OpsAsyncJob job, AsyncJobExecutionContext context) throws Exception {
        JsonNode payload = RagJobPayload.parse(mapper, job);
        long projectId = RagJobPayload.requiredLong(payload, "projectId");
        long ownerUserId = payload.path("ownerUserId").canConvertToLong()
                ? payload.path("ownerUserId").longValue() : job.getOwnerUserId();
        return mapper.writeValueAsString(indexing.rebuildProject(projectId, ownerUserId, context));
    }
}
