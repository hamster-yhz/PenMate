package com.penmate.backend.infrastructure.scheduling;

import com.penmate.backend.application.rag.RagBuildCleanupService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RagMaintenanceScheduler {
    private final RagBuildCleanupService cleanup;

    public RagMaintenanceScheduler(RagBuildCleanupService cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(
            initialDelayString = "${penmate.rag.cleanup-initial-delay:PT10S}",
            fixedDelayString = "${penmate.rag.cleanup-delay:PT1H}")
    public void enqueueSupersededBuildCleanup() {
        cleanup.enqueueSupersededBuilds();
    }
}
