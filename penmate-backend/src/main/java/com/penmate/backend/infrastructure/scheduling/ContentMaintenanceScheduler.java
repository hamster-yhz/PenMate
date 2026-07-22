package com.penmate.backend.infrastructure.scheduling;

import com.penmate.backend.application.novel.ChapterAiUndoRetentionService;
import com.penmate.backend.application.novel.NovelTrashApplicationService;
import com.penmate.backend.application.novel.NovelCoverApplicationService;
import com.penmate.backend.application.storybible.StoryBibleHistoryArchiveService;
import com.penmate.backend.application.iam.AccountDeletionApplicationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ContentMaintenanceScheduler {

    private final ChapterAiUndoRetentionService chapterAiUndoRetention;
    private final StoryBibleHistoryArchiveService storyBibleHistoryArchive;
    private final NovelTrashApplicationService novelTrashApplicationService;
    private final AccountDeletionApplicationService accountDeletionApplicationService;
    private final NovelCoverApplicationService novelCoverApplicationService;

    public ContentMaintenanceScheduler(ChapterAiUndoRetentionService chapterAiUndoRetention,
                                       StoryBibleHistoryArchiveService storyBibleHistoryArchive,
                                       NovelTrashApplicationService novelTrashApplicationService,
                                       AccountDeletionApplicationService accountDeletionApplicationService,
                                       NovelCoverApplicationService novelCoverApplicationService) {
        this.chapterAiUndoRetention = chapterAiUndoRetention;
        this.storyBibleHistoryArchive = storyBibleHistoryArchive;
        this.novelTrashApplicationService = novelTrashApplicationService;
        this.accountDeletionApplicationService = accountDeletionApplicationService;
        this.novelCoverApplicationService = novelCoverApplicationService;
    }

    @Scheduled(cron = "${penmate.chapter-ai-undo-retention-cron:0 40 3 * * ?}")
    public void retainChapterAiUndo() {
        chapterAiUndoRetention.deleteExpired();
    }

    @Scheduled(cron = "${penmate.story-bible.history-archive-cron:0 30 3 * * ?}")
    public void archiveStoryBibleHistory() {
        storyBibleHistoryArchive.archiveEligibleHistory();
    }

    @Scheduled(cron = "${penmate.novel-trash-retention-cron:0 10 4 * * ?}")
    public void purgeExpiredNovelTrash() {
        novelTrashApplicationService.purgeExpiredProjects();
    }

    @Scheduled(cron = "${penmate.account-deletion-retention-cron:0 25 4 * * ?}")
    public void purgeExpiredAccounts() {
        accountDeletionApplicationService.purgeExpiredAccounts();
    }

    @Scheduled(cron = "${penmate.cover-upload-cleanup-cron:0 20 * * * ?}")
    public void purgeExpiredCoverUploads() {
        novelCoverApplicationService.purgeExpiredPendingUploads();
    }
}
