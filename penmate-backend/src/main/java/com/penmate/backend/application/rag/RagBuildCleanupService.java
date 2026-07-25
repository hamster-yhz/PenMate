package com.penmate.backend.application.rag;

import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.ops.AsyncJobQueueService;
import com.penmate.backend.domain.rag.repository.RagIndexRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RagBuildCleanupService {
    private final RagIndexRepository indexes;
    private final AsyncJobQueueService jobs;
    private final JsonCodec jsonCodec;

    public RagBuildCleanupService(RagIndexRepository indexes, AsyncJobQueueService jobs, JsonCodec jsonCodec) {
        this.indexes = indexes;
        this.jobs = jobs;
        this.jsonCodec = jsonCodec;
    }

    public void enqueue(Long ownerUserId, Long projectId, Long buildId) {
        jobs.enqueue("RAG_CLEANUP_EMBEDDING_SPACE", "rag:build:%d:cleanup".formatted(buildId),
                ownerUserId, projectId, jsonCodec.write(Map.of("buildId", buildId)));
    }

    public int enqueueSupersededBuilds() {
        var candidates = indexes.findSupersededBuilds();
        candidates.forEach(candidate -> enqueue(candidate.ownerUserId(), candidate.projectId(), candidate.buildId()));
        return candidates.size();
    }
}
