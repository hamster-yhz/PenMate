package com.penmate.backend.application.agent.run;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
public class AgentCheckpointRetentionService {

    private final AgentCheckpointArchiveService archiveService;

    public AgentCheckpointRetentionService(AgentCheckpointArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    @Scheduled(cron = "${penmate.agent.checkpoint-retention-cron:0 15 3 * * ?}")
    public void scheduledCleanup() {
        Instant now = Instant.now();
        var archived = archiveService.archiveEligible(now);
        var purged = archiveService.purgeExpired(now);
        log.info("Agent checkpoint retention complete: archived={}, purged={}",
                archived.archivedCheckpoints(), purged.purgedCheckpoints());
    }
}
