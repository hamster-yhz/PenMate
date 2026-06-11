package com.penmate.backend.application.agent.run;

import com.penmate.backend.domain.agent.run.repository.AgentCheckpointRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AgentEventRetentionService {

    private static final Logger log = LoggerFactory.getLogger(AgentEventRetentionService.class);
    private static final int RETENTION_DAYS_TERMINAL = 7;
    private static final int MIN_RETAIN_EVENTS = 50;

    private final AgentRunEventRepository eventRepository;
    private final AgentCheckpointRepository checkpointRepository;

    public AgentEventRetentionService(AgentRunEventRepository eventRepository,
                                       AgentCheckpointRepository checkpointRepository) {
        this.eventRepository = eventRepository;
        this.checkpointRepository = checkpointRepository;
    }

    @Scheduled(cron = "${penmate.agent.event-retention-cron:0 0 3 * * ?}")
    @Transactional
    public void scheduledCleanup() {
        log.info("Starting agent event retention cleanup");
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS_TERMINAL);
        int deleted = eventRepository.deleteTerminalEventsOlderThan(cutoff, MIN_RETAIN_EVENTS);
        log.info("Agent event retention cleanup complete: deleted {} events", deleted);
    }

    public void cleanupRun(Long runId, Long latestCheckpointSeq) {
        Long safeSeq = latestCheckpointSeq != null ? latestCheckpointSeq : 0L;
        int deleted = eventRepository.deleteEventsBelowSequence(runId, safeSeq, MIN_RETAIN_EVENTS);
        log.info("Checkpoint compaction for run {}: deleted {} events below seq {}", runId, deleted, safeSeq);
    }
}