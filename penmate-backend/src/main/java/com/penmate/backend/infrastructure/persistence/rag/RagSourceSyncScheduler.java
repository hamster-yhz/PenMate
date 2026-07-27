package com.penmate.backend.infrastructure.persistence.rag;

import com.penmate.backend.application.rag.RagSourceSyncService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RagSourceSyncScheduler {
    private final RagSourceSyncService sourceSync;

    public RagSourceSyncScheduler(RagSourceSyncService sourceSync) {
        this.sourceSync = sourceSync;
    }

    @Scheduled(fixedDelayString = "${penmate.rag.sync.poll-ms:2000}")
    public void processDueSources() {
        sourceSync.processDueSources();
    }
}
