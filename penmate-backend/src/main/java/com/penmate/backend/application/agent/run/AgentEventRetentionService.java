package com.penmate.backend.application.agent.run;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AgentEventRetentionService {

    private static final Logger log = LoggerFactory.getLogger(AgentEventRetentionService.class);
    private final AgentEventArchiveService archiveService;

    public AgentEventRetentionService(AgentEventArchiveService archiveService) {
        this.archiveService = archiveService;
    }

    @Scheduled(cron = "${penmate.agent.event-retention-cron:0 0 3 * * ?}")
    public void scheduledCleanup() {
        log.info("Starting agent event retention cleanup");
        LocalDateTime now = LocalDateTime.now();
        var archived = archiveService.archiveEligible(now);
        var purged = archiveService.purgeExpired(now);
        log.info("Agent Event retention complete: archivedRuns={}, archivedEvents={}, purgedArchives={}",
                archived.archivedRuns(), archived.archivedEvents(), purged.purgedArchives());
    }
}
