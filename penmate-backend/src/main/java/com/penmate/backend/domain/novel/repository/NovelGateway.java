package com.penmate.backend.domain.novel.repository;

import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.model.ChapterAiUndoOperation;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.model.NovelVolume;

import java.util.List;
import java.time.Instant;

public interface NovelGateway {

    List<NovelProject> findAllProjects();

    List<NovelProject> findDeletedProjectsByOwner(Long ownerUserId);

    NovelProject findDeletedProjectByIdAndOwner(Long projectId, Long ownerUserId);

    NovelProject lockDeletedProject(Long projectId, Long ownerUserId, Instant deletedBefore);

    List<Long> findExpiredDeletedProjectIds(Instant deletedBefore);

    List<String> findProjectObjectKeys(Long projectId);

    List<Long> findProjectIdsByOwner(Long ownerUserId);

    NovelProject findProjectById(Long projectId);

    NovelProject lockProject(Long projectId);

    int insertProject(NovelProject project);

    int updateProject(NovelProject project);

    int incrementStructureRevision(Long projectId);

    int softDeleteProject(Long projectId, Long ownerUserId);

    int restoreProject(Long projectId, Long ownerUserId);

    int purgeDeletedProject(Long projectId, Long ownerUserId, Instant deletedBefore);

    List<NovelVolume> findVolumesByProjectId(Long projectId);

    int insertVolume(NovelVolume volume);

    int updateVolume(NovelVolume volume);

    int softDeleteVolume(Long projectId, Long volumeId);

    int softDeleteChaptersByVolume(Long projectId, Long volumeId);

    int countActiveAiChapterLeasesByVolume(Long projectId, Long volumeId);

    List<NovelChapter> findChaptersByProjectId(Long projectId);

    NovelChapter findChapterByIdAndProjectId(Long projectId, Long chapterId);

    int insertChapter(NovelChapter chapter);

    int updateChapter(NovelChapter chapter);

    int acquireChapterAiLease(Long projectId, Long chapterId, Long runId,
                              String leaseToken, Instant expiresAt);

    int renewChapterAiLease(Long projectId, Long chapterId, String leaseToken, Instant expiresAt);

    int releaseChapterAiLease(Long projectId, Long chapterId, String leaseToken);

    int updateUserChapterContent(Long projectId, Long chapterId, Long expectedRevision,
                                 String content, Integer wordCount);

    int updateAiChapterContent(Long projectId, Long chapterId, String leaseToken,
                               Long expectedRevision, String content, Integer wordCount);

    int restoreAiChapterContent(Long projectId, Long chapterId, Long expectedRevision,
                                String expectedContent, String restoredContent, Integer wordCount);

    ChapterAiUndoOperation findAvailableAiUndoByRunAndChapter(Long projectId, Long runId, Long chapterId);

    ChapterAiUndoOperation findAiUndoByOperationId(Long projectId, Long operationId);

    List<ChapterAiUndoOperation> listAvailableAiUndoByChapter(Long projectId, Long chapterId);

    List<ChapterAiUndoOperation> listAvailableAiUndoByProject(Long projectId);

    List<ChapterAiUndoOperation> listAvailableAiUndoByRun(Long projectId, Long runId);

    long nextAiUndoSequence(Long projectId, Long chapterId);

    int insertAiUndo(ChapterAiUndoOperation operation);

    int updateMergedAiUndo(ChapterAiUndoOperation operation);

    int invalidateAvailableAiUndoByChapter(Long projectId, Long chapterId);

    int dismissAvailableAiUndoThrough(Long projectId, Long chapterId, Long sequenceNo);

    int markAiUndoUndone(Long operationId);

    int rebaseAiUndoRevision(Long operationId, Long appliedRevision);

    int deleteExpiredAiUndo(Instant cutoff);

    int softDeleteChapter(Long projectId, Long chapterId);

}

