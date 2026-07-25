package com.penmate.backend.infrastructure.scheduling;

import com.penmate.backend.application.agent.run.AgentCheckpointRetentionService;
import com.penmate.backend.application.agent.run.AgentEventRetentionService;
import com.penmate.backend.application.agent.run.AgentRunReconciler;
import com.penmate.backend.application.approval.AgentRunPendingApprovalTimeoutGuard;
import com.penmate.backend.application.novel.ChapterAiUndoRetentionService;
import com.penmate.backend.application.novel.NovelTrashApplicationService;
import com.penmate.backend.application.iam.AccountDeletionApplicationService;
import com.penmate.backend.application.novel.NovelCoverApplicationService;
import com.penmate.backend.application.ops.AsyncJobWorker;
import com.penmate.backend.application.rag.RagBuildCleanupService;
import com.penmate.backend.application.storybible.StoryBibleHistoryArchiveService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApplicationSchedulersTest {

    @Test
    void agent_scheduler_preserves_triggers_and_delegates_to_use_cases() throws Exception {
        AgentCheckpointRetentionService checkpoints = mock(AgentCheckpointRetentionService.class);
        AgentEventRetentionService events = mock(AgentEventRetentionService.class);
        AgentRunReconciler reconciler = mock(AgentRunReconciler.class);
        AgentMaintenanceScheduler scheduler = new AgentMaintenanceScheduler(checkpoints, events, reconciler);

        scheduler.retainCheckpoints();
        scheduler.retainEvents();
        scheduler.reconcileRuns();

        verify(checkpoints).scheduledCleanup();
        verify(events).scheduledCleanup();
        verify(reconciler).reconcile();
        assertThat(schedule(AgentMaintenanceScheduler.class, "retainCheckpoints").cron())
                .isEqualTo("${penmate.agent.checkpoint-retention-cron:0 15 3 * * ?}");
        assertThat(schedule(AgentMaintenanceScheduler.class, "retainEvents").cron())
                .isEqualTo("${penmate.agent.event-retention-cron:0 0 3 * * ?}");
        assertThat(schedule(AgentMaintenanceScheduler.class, "reconcileRuns").fixedDelayString())
                .isEqualTo("${penmate.agent.reconcile-delay:PT30S}");
    }

    @Test
    void content_scheduler_preserves_triggers_and_delegates_to_use_cases() throws Exception {
        ChapterAiUndoRetentionService chapterUndo = mock(ChapterAiUndoRetentionService.class);
        StoryBibleHistoryArchiveService storyBible = mock(StoryBibleHistoryArchiveService.class);
        NovelTrashApplicationService trash = mock(NovelTrashApplicationService.class);
        AccountDeletionApplicationService accountDeletion = mock(AccountDeletionApplicationService.class);
        NovelCoverApplicationService covers = mock(NovelCoverApplicationService.class);
        ContentMaintenanceScheduler scheduler = new ContentMaintenanceScheduler(chapterUndo, storyBible, trash, accountDeletion, covers);

        scheduler.retainChapterAiUndo();
        scheduler.archiveStoryBibleHistory();
        scheduler.purgeExpiredNovelTrash();
        scheduler.purgeExpiredAccounts();
        scheduler.purgeExpiredCoverUploads();

        verify(chapterUndo).deleteExpired();
        verify(storyBible).archiveEligibleHistory();
        verify(trash).purgeExpiredProjects();
        verify(accountDeletion).purgeExpiredAccounts();
        verify(covers).purgeExpiredPendingUploads();
        assertThat(schedule(ContentMaintenanceScheduler.class, "retainChapterAiUndo").cron())
                .isEqualTo("${penmate.chapter-ai-undo-retention-cron:0 40 3 * * ?}");
        assertThat(schedule(ContentMaintenanceScheduler.class, "archiveStoryBibleHistory").cron())
                .isEqualTo("${penmate.story-bible.history-archive-cron:0 30 3 * * ?}");
        assertThat(schedule(ContentMaintenanceScheduler.class, "purgeExpiredNovelTrash").cron())
                .isEqualTo("${penmate.novel-trash-retention-cron:0 10 4 * * ?}");
        assertThat(schedule(ContentMaintenanceScheduler.class, "purgeExpiredAccounts").cron())
                .isEqualTo("${penmate.account-deletion-retention-cron:0 25 4 * * ?}");
        assertThat(schedule(ContentMaintenanceScheduler.class, "purgeExpiredCoverUploads").cron())
                .isEqualTo("${penmate.cover-upload-cleanup-cron:0 20 * * * ?}");
    }

    @Test
    void approval_and_ops_schedulers_delegate_with_original_delays() throws Exception {
        AgentRunPendingApprovalTimeoutGuard approvals = mock(AgentRunPendingApprovalTimeoutGuard.class);
        AsyncJobWorker jobs = mock(AsyncJobWorker.class);
        ApprovalMaintenanceScheduler approvalScheduler = new ApprovalMaintenanceScheduler(approvals);
        OpsJobScheduler jobScheduler = new OpsJobScheduler(jobs);

        approvalScheduler.expireTimedOutApprovals();
        jobScheduler.poll();

        verify(approvals).failTimedOutResumingApprovals();
        verify(jobs).poll();
        assertThat(schedule(ApprovalMaintenanceScheduler.class, "expireTimedOutApprovals").fixedDelayString())
                .isEqualTo("PT1M");
        assertThat(schedule(OpsJobScheduler.class, "poll").fixedDelayString())
                .isEqualTo("${penmate.jobs.poll-delay:PT1S}");
    }

    @Test
    void rag_maintenance_enqueues_superseded_build_cleanup() throws Exception {
        RagBuildCleanupService cleanup = mock(RagBuildCleanupService.class);
        RagMaintenanceScheduler scheduler = new RagMaintenanceScheduler(cleanup);

        scheduler.enqueueSupersededBuildCleanup();

        verify(cleanup).enqueueSupersededBuilds();
        Scheduled scheduled = schedule(RagMaintenanceScheduler.class, "enqueueSupersededBuildCleanup");
        assertThat(scheduled.initialDelayString()).isEqualTo("${penmate.rag.cleanup-initial-delay:PT10S}");
        assertThat(scheduled.fixedDelayString()).isEqualTo("${penmate.rag.cleanup-delay:PT1H}");
    }

    private Scheduled schedule(Class<?> type, String method) throws Exception {
        return type.getMethod(method).getAnnotation(Scheduled.class);
    }
}
