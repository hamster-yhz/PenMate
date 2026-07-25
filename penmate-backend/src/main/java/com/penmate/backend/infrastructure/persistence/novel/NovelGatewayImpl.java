package com.penmate.backend.infrastructure.persistence.novel;

import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.model.ChapterAiUndoOperation;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.model.NovelVolume;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * NovelGatewayImpl。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Repository
public class NovelGatewayImpl implements NovelGateway {

    private final NovelProjectMapper novelProjectMapper;
    private final NovelVolumeMapper novelVolumeMapper;
    private final NovelChapterMapper novelChapterMapper;
    private final ChapterAiUndoMapper chapterAiUndoMapper;

    public NovelGatewayImpl(NovelProjectMapper novelProjectMapper,
                            NovelVolumeMapper novelVolumeMapper,
                            NovelChapterMapper novelChapterMapper,
                            ChapterAiUndoMapper chapterAiUndoMapper) {
        this.novelProjectMapper = novelProjectMapper;
        this.novelVolumeMapper = novelVolumeMapper;
        this.novelChapterMapper = novelChapterMapper;
        this.chapterAiUndoMapper = chapterAiUndoMapper;
    }

    /**
     * 处理业务请求。
     *
     * @param novelProjectMapper.findAll( 入参：novelProjectMapper.findAll(
     * @return 出参：处理结果
     */
    @Override
    public List<NovelProject> findAllProjects() { return novelProjectMapper.findAll(); }

    @Override
    public List<NovelProject> findDeletedProjectsByOwner(Long ownerUserId) {
        return novelProjectMapper.findDeletedByOwner(ownerUserId);
    }

    @Override
    public NovelProject findDeletedProjectByIdAndOwner(Long projectId, Long ownerUserId) {
        return novelProjectMapper.findDeletedByProjectIdAndOwner(projectId, ownerUserId);
    }

    @Override
    public NovelProject lockDeletedProject(Long projectId, Long ownerUserId, java.time.Instant deletedBefore) {
        return novelProjectMapper.lockDeletedProject(projectId, ownerUserId, deletedBefore);
    }

    @Override
    public List<Long> findExpiredDeletedProjectIds(java.time.Instant deletedBefore) {
        return novelProjectMapper.findExpiredDeletedProjectIds(deletedBefore);
    }

    @Override
    public List<String> findProjectObjectKeys(Long projectId) {
        return novelProjectMapper.findProjectObjectKeys(projectId);
    }

    @Override
    public List<Long> findProjectIdsByOwner(Long ownerUserId) {
        return novelProjectMapper.findProjectIdsByOwner(ownerUserId);
    }

    /**
     * 处理业务请求。
     *
     * @param novelProjectMapper.findById(projectId 入参：novelProjectMapper.findById(projectId
     * @return 出参：处理结果
     */
    @Override
    public NovelProject findProjectById(Long projectId) { return novelProjectMapper.findByProjectId(projectId); }

    @Override
    public NovelProject lockProject(Long projectId) { return novelProjectMapper.lockByProjectId(projectId); }

    /**
     * 处理业务请求。
     *
     * @param novelProjectMapper.insert(project 入参：novelProjectMapper.insert(project
     * @return 出参：处理结果
     */
    @Override
    public int insertProject(NovelProject project) { return novelProjectMapper.insert(project); }

    /**
     * 更新业务数据。
     *
     * @param novelProjectMapper.update(project 入参：novelProjectMapper.update(project
     * @return 出参：处理结果
     */
    @Override
    public int updateProject(NovelProject project) { return novelProjectMapper.update(project); }

    @Override
    public int incrementStructureRevision(Long projectId) { return novelProjectMapper.incrementStructureRevision(projectId); }

    /**
     * 处理业务请求。
     *
     * @param novelProjectMapper.softDelete(projectId 入参：novelProjectMapper.softDelete(projectId
     * @return 出参：处理结果
     */
    @Override
    public int softDeleteProject(Long projectId, Long ownerUserId) {
        return novelProjectMapper.softDelete(projectId, ownerUserId);
    }

    @Override
    public int restoreProject(Long projectId, Long ownerUserId) {
        return novelProjectMapper.restore(projectId, ownerUserId);
    }

    @Override
    public int purgeDeletedProject(Long projectId, Long ownerUserId, java.time.Instant deletedBefore) {
        return novelProjectMapper.purgeDeleted(projectId, ownerUserId, deletedBefore);
    }

    /**
     * 处理业务请求。
     *
     * @param novelVolumeMapper.findByProjectId(projectId 入参：novelVolumeMapper.findByProjectId(projectId
     * @return 出参：处理结果
     */
    @Override
    public List<NovelVolume> findVolumesByProjectId(Long projectId) { return novelVolumeMapper.findByProjectId(projectId); }

    /**
     * 处理业务请求。
     *
     * @param novelVolumeMapper.insert(volume 入参：novelVolumeMapper.insert(volume
     * @return 出参：处理结果
     */
    @Override
    public int insertVolume(NovelVolume volume) { return novelVolumeMapper.insert(volume); }

    /**
     * 更新业务数据。
     *
     * @param novelVolumeMapper.update(volume 入参：novelVolumeMapper.update(volume
     * @return 出参：处理结果
     */
    @Override
    public int updateVolume(NovelVolume volume) { return novelVolumeMapper.update(volume); }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param volumeId 入参：volumeId
     * @return 出参：处理结果
     */
    @Override
    public int softDeleteVolume(Long projectId, Long volumeId) { return novelVolumeMapper.softDelete(projectId, volumeId); }

    @Override
    public int softDeleteChaptersByVolume(Long projectId, Long volumeId) {
        return novelChapterMapper.softDeleteByVolume(projectId, volumeId);
    }

    @Override
    public int countActiveAiChapterLeasesByVolume(Long projectId, Long volumeId) {
        return novelChapterMapper.countActiveAiLeasesByVolume(projectId, volumeId);
    }

    /**
     * 处理业务请求。
     *
     * @param novelChapterMapper.findByProjectId(projectId 入参：novelChapterMapper.findByProjectId(projectId
     * @return 出参：处理结果
     */
    @Override
    public List<NovelChapter> findChaptersByProjectId(Long projectId) { return novelChapterMapper.findByProjectId(projectId); }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @return 出参：处理结果
     */
    @Override
    public NovelChapter findChapterByIdAndProjectId(Long projectId, Long chapterId) { return novelChapterMapper.findByIdAndProjectId(projectId, chapterId); }

    /**
     * 处理业务请求。
     *
     * @param novelChapterMapper.insert(chapter 入参：novelChapterMapper.insert(chapter
     * @return 出参：处理结果
     */
    @Override
    public int insertChapter(NovelChapter chapter) { return novelChapterMapper.insert(chapter); }

    /**
     * 更新业务数据。
     *
     * @param novelChapterMapper.update(chapter 入参：novelChapterMapper.update(chapter
     * @return 出参：处理结果
     */
    @Override
    public int updateChapter(NovelChapter chapter) { return novelChapterMapper.update(chapter); }

    @Override
    public int acquireChapterAiLease(Long projectId, Long chapterId, Long runId,
                                     String leaseToken, java.time.Instant expiresAt) {
        return novelChapterMapper.acquireAiLease(projectId, chapterId, runId, leaseToken, expiresAt);
    }

    @Override
    public int renewChapterAiLease(Long projectId, Long chapterId, String leaseToken, java.time.Instant expiresAt) {
        return novelChapterMapper.renewAiLease(projectId, chapterId, leaseToken, expiresAt);
    }

    @Override
    public int releaseChapterAiLease(Long projectId, Long chapterId, String leaseToken) {
        return novelChapterMapper.releaseAiLease(projectId, chapterId, leaseToken);
    }

    @Override
    public int updateUserChapterContent(Long projectId, Long chapterId, Long expectedRevision,
                                        String content, Integer wordCount) {
        return novelChapterMapper.updateUserContent(projectId, chapterId, expectedRevision, content, wordCount);
    }

    @Override
    public int updateAiChapterContent(Long projectId, Long chapterId, String leaseToken, Long expectedRevision,
                                      String content, Integer wordCount) {
        return novelChapterMapper.updateAiContent(projectId, chapterId, leaseToken, expectedRevision, content, wordCount);
    }

    @Override
    public int restoreAiChapterContent(Long projectId, Long chapterId, Long expectedRevision,
                                       String expectedContent, String restoredContent, Integer wordCount) {
        return novelChapterMapper.restoreAiContent(projectId, chapterId, expectedRevision,
                expectedContent, restoredContent, wordCount);
    }

    @Override
    public ChapterAiUndoOperation findAvailableAiUndoByRunAndChapter(Long projectId, Long runId, Long chapterId) {
        return chapterAiUndoMapper.findAvailableByRunAndChapter(projectId, runId, chapterId);
    }

    @Override
    public ChapterAiUndoOperation findAiUndoByOperationId(Long projectId, Long operationId) {
        return chapterAiUndoMapper.findByOperationId(projectId, operationId);
    }

    @Override
    public List<ChapterAiUndoOperation> listAvailableAiUndoByChapter(Long projectId, Long chapterId) {
        return chapterAiUndoMapper.listAvailableByChapter(projectId, chapterId);
    }

    @Override
    public List<ChapterAiUndoOperation> listAvailableAiUndoByRun(Long projectId, Long runId) {
        return chapterAiUndoMapper.listAvailableByRun(projectId, runId);
    }

    @Override
    public long nextAiUndoSequence(Long projectId, Long chapterId) {
        return chapterAiUndoMapper.nextSequence(projectId, chapterId);
    }

    @Override
    public int insertAiUndo(ChapterAiUndoOperation operation) { return chapterAiUndoMapper.insert(operation); }

    @Override
    public int updateMergedAiUndo(ChapterAiUndoOperation operation) {
        return chapterAiUndoMapper.updateMergedResult(operation);
    }

    @Override
    public int invalidateAvailableAiUndoByChapter(Long projectId, Long chapterId) {
        return chapterAiUndoMapper.invalidateAvailableByChapter(projectId, chapterId);
    }

    @Override
    public int markAiUndoUndone(Long operationId) { return chapterAiUndoMapper.markUndone(operationId); }

    @Override
    public int rebaseAiUndoRevision(Long operationId, Long appliedRevision) {
        return chapterAiUndoMapper.rebaseAppliedRevision(operationId, appliedRevision);
    }

    @Override
    public int deleteExpiredAiUndo(java.time.Instant cutoff) { return chapterAiUndoMapper.deleteExpired(cutoff); }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @return 出参：处理结果
     */
    @Override
    public int softDeleteChapter(Long projectId, Long chapterId) { return novelChapterMapper.softDelete(projectId, chapterId); }

}
