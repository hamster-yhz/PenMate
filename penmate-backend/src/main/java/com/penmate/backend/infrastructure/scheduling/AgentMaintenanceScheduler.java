package com.penmate.backend.infrastructure.scheduling;

import com.penmate.backend.application.agent.run.AgentCheckpointRetentionService;
import com.penmate.backend.application.agent.run.AgentEventRetentionService;
import com.penmate.backend.application.agent.run.AgentRunReconciler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AgentMaintenanceScheduler {

    private final AgentCheckpointRetentionService checkpointRetention;
    private final AgentEventRetentionService eventRetention;
    private final AgentRunReconciler runReconciler;

    public AgentMaintenanceScheduler(AgentCheckpointRetentionService checkpointRetention,
                                     AgentEventRetentionService eventRetention,
                                     AgentRunReconciler runReconciler) {
        this.checkpointRetention = checkpointRetention;
        this.eventRetention = eventRetention;
        this.runReconciler = runReconciler;
    }

    @Scheduled(cron = "${penmate.agent.checkpoint-retention-cron:0 15 3 * * ?}")
    public void retainCheckpoints() {
        checkpointRetention.scheduledCleanup();
    }

    @Scheduled(cron = "${penmate.agent.event-retention-cron:0 0 3 * * ?}")
    public void retainEvents() {
        eventRetention.scheduledCleanup();
    }

    @Scheduled(fixedDelayString = "${penmate.agent.reconcile-delay:PT30S}")
    public void reconcileRuns() {
        runReconciler.reconcile();
    }
}
