package com.penmate.backend.application.novel;

import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.model.NovelChapterVersion;
import com.penmate.backend.domain.novel.model.NovelOutlineNode;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.model.NovelVolume;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.shared.service.ObjectStorageService;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import com.penmate.backend.application.storybible.StoryBibleApplicationService;
import com.penmate.backend.application.novel.command.NovelCommands.CommitChapterContentCommand;
import com.penmate.backend.application.novel.command.NovelCommands.CreateChapterCommand;
import com.penmate.backend.application.novel.command.NovelCommands.CreateChapterVersionCommand;
import com.penmate.backend.application.novel.command.NovelCommands.CreateOutlineNodeCommand;
import com.penmate.backend.application.novel.command.NovelCommands.CreateProjectCommand;
import com.penmate.backend.application.novel.command.NovelCommands.CreateVolumeCommand;
import com.penmate.backend.application.novel.command.NovelCommands.MoveOutlineNodeCommand;
import com.penmate.backend.application.novel.command.NovelCommands.MoveChapterCommand;
import com.penmate.backend.application.novel.command.NovelCommands.UpdateChapterCommand;
import com.penmate.backend.application.novel.command.NovelCommands.UpdateOutlineNodeCommand;
import com.penmate.backend.application.novel.command.NovelCommands.UpdateProjectCommand;
import com.penmate.backend.application.novel.command.NovelCommands.UpdateVolumeCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 小说项目应用服务。
 * <p>负责项目、卷章、成员、大纲、卡片与章节版本的全链路业务编排，并发布实时事件与审计日志。</p>
 */
@Service
@Slf4j
public class NovelApplicationService {

    private final NovelGateway novelGateway;
    private final BusinessIdGenerator businessIdGenerator;
    private final RealtimeEventService realtimeEventService;
    private final ObjectStorageService objectStorageService;
    private final StoryBibleApplicationService storyBibleApplicationService;

    public NovelApplicationService(NovelGateway novelGateway,
                                   BusinessIdGenerator businessIdGenerator,
                                   RealtimeEventService realtimeEventService,
                                   ObjectStorageService objectStorageService,
                                   StoryBibleApplicationService storyBibleApplicationService) {
        this.novelGateway = novelGateway;
        this.businessIdGenerator = businessIdGenerator;
        this.realtimeEventService = realtimeEventService;
        this.objectStorageService = objectStorageService;
        this.storyBibleApplicationService = storyBibleApplicationService;
    }

    /**
     * 查询所有小说项目。
     *
     * @return 出参：处理结果
     */
    public List<NovelProject> listProjects() {
        List<NovelProject> projects = novelGateway.findAllProjects();
        log.info("查询小说项目列表: count={}", projects.size());
        return projects;
    }

    /**
     * 查询项目详情。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public NovelProject getProject(Long projectId) {
        log.info("查询小说项目详情: projectId={}", projectId);
        NovelProject project = novelGateway.findProjectById(projectId);
        if (project == null) {
            log.warn("查询小说项目详情失败: projectId={}, reason=not_found", projectId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Project not found");
        }
        log.info("查询小说项目详情成功: projectId={}, title={}", projectId, project.getTitle());
        return project;
    }

    /**
     * 创建小说项目。
     *
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @Transactional
    public NovelProject createProject(CreateProjectCommand command, String traceId) {
        log.info("创建小说项目: ownerUserId={}, title={}", command.ownerUserId(), command.title());
        NovelProject project = new NovelProject();
        project.setProjectId(businessIdGenerator.nextId());
        project.setOwnerUserId(command.ownerUserId());
        project.setTitle(command.title());
        project.setSummary(command.summary());
        project.setStatus(command.status() == null ? 1 : command.status());
        project.setStructureRevision(1L);
        int affected = novelGateway.insertProject(project);
        if (affected != 1) {
            log.error("创建小说项目失败: ownerUserId={}, title={}", command.ownerUserId(), command.title());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create project");
        }
        storyBibleApplicationService.bootstrap(
                project.getProjectId(),
                project.getTitle(),
                command.ownerUserId()
        );
         
        log.info("创建小说项目成功: projectId={}, ownerUserId={}", project.getProjectId(), command.ownerUserId());
        return project;
    }

    /**
     * 更新小说项目基础信息。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelProject updateProject(Long projectId, UpdateProjectCommand command, String traceId) {
        log.info("更新小说项目: projectId={}, title={}", projectId, command.title());
        NovelProject existing = getProject(projectId);
        existing.setTitle(command.title());
        existing.setSummary(command.summary());
        existing.setStatus(command.status() == null ? existing.getStatus() : command.status());
        int affected = novelGateway.updateProject(existing);
        if (affected != 1) {
            log.error("更新小说项目失败: projectId={}", projectId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to update project");
        }
         
        log.info("更新小说项目成功: projectId={}", projectId);
        return getProject(projectId);
    }

    /**
     * 删除小说项目（软删除）。
     *
     * @param projectId 入参：projectId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void deleteProject(Long projectId, Long operatorId, String traceId) {
        log.info("删除小说项目: projectId={}, operatorId={}", projectId, operatorId);
        int affected = novelGateway.softDeleteProject(projectId);
        if (affected != 1) {
            log.warn("删除小说项目失败: projectId={}, reason=not_found_or_deleted", projectId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Project not found or already deleted");
        }
         
        log.info("删除小说项目成功: projectId={}", projectId);
    }

    /**
     * 查询项目分卷列表。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<NovelVolume> listVolumes(Long projectId) {
        List<NovelVolume> volumes = novelGateway.findVolumesByProjectId(projectId);
        log.info("查询分卷列表: projectId={}, count={}", projectId, volumes.size());
        return volumes;
    }

    /**
     * 创建分卷。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @Transactional
    public NovelVolume createVolume(Long projectId, CreateVolumeCommand command, Long operatorId, String traceId) {
        log.info("创建分卷: projectId={}, title={}, operatorId={}", projectId, command.title(), operatorId);
        NovelVolume volume = new NovelVolume();
        volume.setVolumeId(businessIdGenerator.nextId());
        volume.setProjectId(projectId);
        volume.setTitle(command.title());
        volume.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
        volume.setDescription(command.description());
        int affected = novelGateway.insertVolume(volume);
        if (affected != 1) {
            log.error("创建分卷失败: projectId={}, title={}", projectId, command.title());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create volume");
        }
        incrementStructureRevision(projectId);
         
        log.info("创建分卷成功: projectId={}, volumeId={}", projectId, volume.getVolumeId());
        return volume;
    }

    /**
     * 更新分卷信息。
     *
     * @param projectId 入参：projectId
     * @param volumeId 入参：volumeId
     * @param command 入参：command
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @Transactional
    public NovelVolume updateVolume(Long projectId, Long volumeId, UpdateVolumeCommand command, Long operatorId, String traceId) {
        log.info("更新分卷: projectId={}, volumeId={}, operatorId={}", projectId, volumeId, operatorId);
        NovelVolume existing = listVolumes(projectId).stream()
                .filter(item -> volumeId.equals(item.getVolumeId()))
                .findFirst()
                .orElse(null);
        NovelVolume volume = new NovelVolume();
        volume.setVolumeId(volumeId);
        volume.setProjectId(projectId);
        volume.setTitle(command.title());
        volume.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
        volume.setDescription(command.description());
        int affected = novelGateway.updateVolume(volume);
        if (affected != 1) {
            log.warn("更新分卷失败: projectId={}, volumeId={}, reason=not_found_or_deleted", projectId, volumeId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Volume not found or already deleted");
        }
        if (existing == null || !Objects.equals(existing.getSortOrder(), volume.getSortOrder())) {
            incrementStructureRevision(projectId);
        }
         
        log.info("更新分卷成功: projectId={}, volumeId={}", projectId, volumeId);
        return listVolumes(projectId).stream().filter(v -> volumeId.equals(v.getVolumeId())).findFirst().orElse(volume);
    }

    /**
     * 删除分卷（软删除）。
     *
     * @param projectId 入参：projectId
     * @param volumeId 入参：volumeId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    @Transactional
    public void deleteVolume(Long projectId, Long volumeId, Long operatorId, String traceId) {
        log.info("删除分卷: projectId={}, volumeId={}, operatorId={}", projectId, volumeId, operatorId);
        int affected = novelGateway.softDeleteVolume(projectId, volumeId);
        if (affected != 1) {
            log.warn("删除分卷失败: projectId={}, volumeId={}, reason=not_found_or_deleted", projectId, volumeId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Volume not found or already deleted");
        }
        incrementStructureRevision(projectId);
         
        log.info("删除分卷成功: projectId={}, volumeId={}", projectId, volumeId);
    }

    /**
     * 查询项目章节列表。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<NovelChapter> listChapters(Long projectId) {
        List<NovelChapter> chapters = novelGateway.findChaptersByProjectId(projectId);
        for (int index = 0; index < chapters.size(); index++) {
            chapters.get(index).setDisplayNo(index + 1);
        }
        log.info("查询章节列表: projectId={}, count={}", projectId, chapters.size());
        return chapters;
    }

    /**
     * 查询章节详情。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @return 出参：处理结果
     */
    public NovelChapter getChapter(Long projectId, Long chapterId) {
        log.info("查询章节详情: projectId={}, chapterId={}", projectId, chapterId);
        NovelChapter chapter = novelGateway.findChapterByIdAndProjectId(projectId, chapterId);
        if (chapter == null) {
            log.warn("查询章节详情失败: projectId={}, chapterId={}, reason=not_found", projectId, chapterId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Chapter not found");
        }
        applyDisplayNo(projectId, chapter);
        log.info("查询章节详情成功: projectId={}, chapterId={}, title={}", projectId, chapterId, chapter.getTitle());
        return chapter;
    }

    /**
     * 创建章节。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @Transactional
    public NovelChapter createChapter(Long projectId, CreateChapterCommand command, Long operatorId, String traceId) {
        log.info("创建章节: projectId={}, title={}, sortOrder={}, operatorId={}", projectId, command.title(), command.sortOrder(), operatorId);
        NovelChapter chapter = new NovelChapter();
        chapter.setChapterId(businessIdGenerator.nextId());
        chapter.setProjectId(projectId);
        chapter.setVolumeId(command.volumeId());
        chapter.setOutlineNodeId(command.outlineNodeId());
        chapter.setTitle(command.title());
        chapter.setSortOrder(command.sortOrder());
        chapter.setStatus(command.status() == null ? 1 : command.status());
        chapter.setWordCount(command.wordCount() == null ? 0 : command.wordCount());
        chapter.setExcerpt(command.excerpt());
        chapter.setContentObjectKey(command.contentObjectKey() == null ? "" : command.contentObjectKey());
        chapter.setContentEtag(command.contentEtag());
        chapter.setContentSize(command.contentSize());
        chapter.setContentChecksum(command.contentChecksum());
        chapter.setStorageProvider(command.storageProvider() == null ? "s3" : command.storageProvider());
        int affected = novelGateway.insertChapter(chapter);
        if (affected != 1) {
            log.error("创建章节失败: projectId={}, title={}", projectId, command.title());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create chapter");
        }
        incrementStructureRevision(projectId);
        applyDisplayNo(projectId, chapter);
         
        log.info("创建章节成功: projectId={}, chapterId={}", projectId, chapter.getChapterId());
        return chapter;
    }

    /**
     * 更新章节元数据。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param command 入参：command
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @Transactional
    public NovelChapter updateChapter(Long projectId, Long chapterId, UpdateChapterCommand command, Long operatorId, String traceId) {
        log.info("更新章节: projectId={}, chapterId={}, operatorId={}", projectId, chapterId, operatorId);
        NovelChapter chapter = getChapter(projectId, chapterId);
        boolean structureChanged = !Objects.equals(chapter.getVolumeId(), command.volumeId())
                || !Objects.equals(chapter.getSortOrder(), command.sortOrder());
        chapter.setVolumeId(command.volumeId());
        chapter.setOutlineNodeId(command.outlineNodeId());
        chapter.setTitle(command.title());
        chapter.setSortOrder(command.sortOrder());
        chapter.setStatus(command.status() == null ? chapter.getStatus() : command.status());
        chapter.setWordCount(command.wordCount() == null ? chapter.getWordCount() : command.wordCount());
        chapter.setExcerpt(command.excerpt());
        chapter.setContentObjectKey(command.contentObjectKey() == null ? chapter.getContentObjectKey() : command.contentObjectKey());
        chapter.setContentEtag(command.contentEtag());
        chapter.setContentSize(command.contentSize());
        chapter.setContentChecksum(command.contentChecksum());
        chapter.setStorageProvider(command.storageProvider() == null ? chapter.getStorageProvider() : command.storageProvider());
        int affected = novelGateway.updateChapter(chapter);
        if (affected != 1) {
            log.error("更新章节失败: projectId={}, chapterId={}", projectId, chapterId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to update chapter");
        }
        if (structureChanged) {
            incrementStructureRevision(projectId);
        }
         
        log.info("更新章节成功: projectId={}, chapterId={}", projectId, chapterId);
        return getChapter(projectId, chapterId);
    }

    @Transactional
    public NovelChapter moveChapter(Long projectId, Long chapterId, MoveChapterCommand command, Long operatorId, String traceId) {
        NovelChapter chapter = getChapter(projectId, chapterId);
        if (Objects.equals(chapter.getVolumeId(), command.volumeId())
                && Objects.equals(chapter.getSortOrder(), command.sortOrder())) {
            return chapter;
        }
        chapter.setVolumeId(command.volumeId());
        chapter.setSortOrder(command.sortOrder());
        if (novelGateway.updateChapter(chapter) != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to move chapter");
        }
        incrementStructureRevision(projectId);
        return getChapter(projectId, chapterId);
    }

    /**
     * 删除章节（软删除）。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    @Transactional
    public void deleteChapter(Long projectId, Long chapterId, Long operatorId, String traceId) {
        log.info("删除章节: projectId={}, chapterId={}, operatorId={}", projectId, chapterId, operatorId);
        int affected = novelGateway.softDeleteChapter(projectId, chapterId);
        if (affected != 1) {
            log.warn("删除章节失败: projectId={}, chapterId={}, reason=not_found_or_deleted", projectId, chapterId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Chapter not found or already deleted");
        }
        incrementStructureRevision(projectId);
         
        log.info("删除章节成功: projectId={}, chapterId={}", projectId, chapterId);
    }

    /**
     * 发布章节。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void publishChapter(Long projectId, Long chapterId, Long operatorId, String traceId) {
        log.info("发布章节: projectId={}, chapterId={}, operatorId={}", projectId, chapterId, operatorId);
        int affected = novelGateway.publishChapter(projectId, chapterId);
        if (affected != 1) {
            log.warn("发布章节失败: projectId={}, chapterId={}, reason=not_found_or_deleted", projectId, chapterId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Chapter not found or already deleted");
        }
         
        log.info("发布章节成功: projectId={}, chapterId={}", projectId, chapterId);
    }

    /**
     * 查询章节版本列表。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @return 出参：处理结果
     */
    public List<NovelChapterVersion> listChapterVersions(Long projectId, Long chapterId) {
        getChapter(projectId, chapterId);
        List<NovelChapterVersion> versions = novelGateway.findVersionsByChapterId(chapterId);
        log.info("查询章节版本列表: projectId={}, chapterId={}, count={}", projectId, chapterId, versions.size());
        return versions;
    }

    /**
     * 创建章节版本快照。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelChapterVersion createChapterVersion(Long projectId, Long chapterId, CreateChapterVersionCommand command, String traceId) {
        log.info("创建章节版本: projectId={}, chapterId={}, changeType={}, createdBy={}", projectId, chapterId, command.changeType(), command.createdBy());
        NovelChapter chapter = getChapter(projectId, chapterId);
        NovelChapterVersion version = new NovelChapterVersion();
        version.setChapterId(chapterId);
        // 复杂流程解析：基于当前最大版本号自增，保证版本链连续且可追溯。
        Integer maxVersionNo = novelGateway.maxVersionNo(chapterId);
        version.setVersionNo((maxVersionNo == null ? 0 : maxVersionNo) + 1);
        version.setChangeType(command.changeType());
        version.setChangeReason(command.changeReason());
        version.setSnapshotObjectKey(chapter.getContentObjectKey());
        version.setSnapshotEtag(chapter.getContentEtag());
        version.setSnapshotSize(chapter.getContentSize());
        version.setSnapshotChecksum(chapter.getContentChecksum());
        version.setCreatedBy(command.createdBy());
        int affected = novelGateway.insertChapterVersion(version);
        if (affected != 1) {
            log.error("创建章节版本失败: projectId={}, chapterId={}", projectId, chapterId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create chapter version");
        }
         
        log.info("创建章节版本成功: projectId={}, chapterId={}, versionNo={}", projectId, chapterId, version.getVersionNo());
        return version;
    }

    /**
     * 查询章节指定版本详情。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param versionNo 入参：versionNo
     * @return 出参：处理结果
     */
    public NovelChapterVersion getChapterVersion(Long projectId, Long chapterId, Integer versionNo) {
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(chapterId, "chapterId must not be null");
        Objects.requireNonNull(versionNo, "versionNo must not be null");
        log.info("查询章节版本详情: projectId={}, chapterId={}, versionNo={}", projectId, chapterId, versionNo);
        getChapter(projectId, chapterId);
        NovelChapterVersion version = novelGateway.findVersionByChapterAndVersion(chapterId, versionNo);
        if (version == null) {
            log.warn("查询章节版本详情失败: projectId={}, chapterId={}, versionNo={}, reason=not_found", projectId, chapterId, versionNo);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Chapter version not found");
        }
        log.info("查询章节版本详情成功: projectId={}, chapterId={}, versionNo={}", projectId, chapterId, versionNo);
        return version;
    }

    /**
     * 按版本号恢复章节内容元数据。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param versionNo 入参：versionNo
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelChapter restoreChapterVersion(Long projectId, Long chapterId, Integer versionNo, Long operatorId, String traceId) {
        log.info("恢复章节版本: projectId={}, chapterId={}, versionNo={}, operatorId={}", projectId, chapterId, versionNo, operatorId);
        NovelChapterVersion version = getChapterVersion(projectId, chapterId, versionNo);
        // 复杂流程解析：恢复版本时仅回滚内容元数据，不覆盖章节业务字段，确保恢复行为可审计。
        int affected = novelGateway.updateChapterContentMeta(projectId, chapterId, version.getSnapshotObjectKey(), version.getSnapshotEtag(), version.getSnapshotSize(), version.getSnapshotChecksum(), "s3");
        if (affected != 1) {
            log.error("恢复章节版本失败: projectId={}, chapterId={}, versionNo={}", projectId, chapterId, versionNo);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to restore chapter version");
        }
         
        log.info("恢复章节版本成功: projectId={}, chapterId={}, versionNo={}", projectId, chapterId, versionNo);
        return getChapter(projectId, chapterId);
    }

    /**
     * 获取章节正文读取地址。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @return 出参：处理结果
     */
    public Map<String, String> getChapterContentUrl(Long projectId, Long chapterId) {
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(chapterId, "chapterId must not be null");
        NovelChapter chapter = getChapter(projectId, chapterId);
        if (chapter.getContentObjectKey() == null || chapter.getContentObjectKey().isBlank()) {
            log.warn("章节正文对象键为空: projectId={}, chapterId={}", projectId, chapterId);
            return Map.of("url", "");
        }
        log.info("获取章节正文读取地址: projectId={}, chapterId={}, objectKey={}", projectId, chapterId, chapter.getContentObjectKey());
        return Map.of("url", objectStorageService.buildReadUrl(chapter.getContentObjectKey()));
    }

    /**
     * 服务端直接读取章节正文文本。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @return 出参：章节正文文本
     */
    public String getChapterContentText(Long projectId, Long chapterId) {
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(chapterId, "chapterId must not be null");
        NovelChapter chapter = getChapter(projectId, chapterId);
        if (chapter.getContentObjectKey() == null || chapter.getContentObjectKey().isBlank()) {
            log.warn("章节正文对象键为空，返回空文本: projectId={}, chapterId={}", projectId, chapterId);
            return "";
        }
        log.info("服务端读取章节正文文本: projectId={}, chapterId={}, objectKey={}", projectId, chapterId, chapter.getContentObjectKey());
        return objectStorageService.readText(chapter.getContentObjectKey());
    }

    /**
     * 获取章节正文上传地址。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @return 出参：处理结果
     */
    public Map<String, String> getChapterContentUploadUrl(Long projectId, Long chapterId) {
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(chapterId, "chapterId must not be null");
        getChapter(projectId, chapterId);
        String objectKey = "novels/" + projectId + "/chapters/" + chapterId + "/" + UUID.randomUUID() + ".md";
        log.info("获取章节正文上传地址: projectId={}, chapterId={}, objectKey={}", projectId, chapterId, objectKey);
        return Map.of(
                "objectKey", objectKey,
                "uploadUrl", objectStorageService.buildUploadUrl(objectKey, "text/plain; charset=utf-8")
        );
    }

    /**
     * 提交章节正文对象存储元数据。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param command 入参：command
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelChapter commitChapterContent(Long projectId, Long chapterId, CommitChapterContentCommand command, Long operatorId, String traceId) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(chapterId, "chapterId must not be null");
        Objects.requireNonNull(command.objectKey(), "objectKey must not be null");
        Objects.requireNonNull(operatorId, "operatorId must not be null");
        log.info("提交章节正文元数据: projectId={}, chapterId={}, objectKey={}, operatorId={}", projectId, chapterId, command.objectKey(), operatorId);
        if (command.content() != null && !command.content().isBlank()) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Direct upload mode does not accept content in commit payload");
        }
        int affected = novelGateway.updateChapterContentMeta(
                projectId,
                chapterId,
                command.objectKey(),
                command.etag(),
                command.size(),
                command.checksum(),
                command.storageProvider() == null ? "s3" : command.storageProvider()
        );
        if (affected != 1) {
            log.error("提交章节正文元数据失败: projectId={}, chapterId={}", projectId, chapterId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to commit chapter content");
        }
         
        log.info("提交章节正文元数据成功: projectId={}, chapterId={}", projectId, chapterId);
        return getChapter(projectId, chapterId);
    }

    /**
     * 获取章节版本快照读取地址。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param versionNo 入参：versionNo
     * @return 出参：处理结果
     */
    public Map<String, String> getChapterVersionSnapshotUrl(Long projectId, Long chapterId, Integer versionNo) {
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(chapterId, "chapterId must not be null");
        Objects.requireNonNull(versionNo, "versionNo must not be null");
        NovelChapterVersion version = getChapterVersion(projectId, chapterId, versionNo);
        if (version.getSnapshotObjectKey() == null || version.getSnapshotObjectKey().isBlank()) {
            log.warn("章节版本快照对象键为空: projectId={}, chapterId={}, versionNo={}", projectId, chapterId, versionNo);
            return Map.of("url", "");
        }
        log.info("获取章节版本快照地址: projectId={}, chapterId={}, versionNo={}", projectId, chapterId, versionNo);
        return Map.of("url", objectStorageService.buildReadUrl(version.getSnapshotObjectKey()));
    }

    /**
     * 查询项目大纲树。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<NovelOutlineNode> listOutlineTree(Long projectId) {
        getProject(projectId);
        List<NovelOutlineNode> nodes = novelGateway.findOutlineNodesByProjectId(projectId);
        log.info("查询大纲树: projectId={}, count={}", projectId, nodes.size());
        return nodes;
    }

    /**
     * 创建大纲节点。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelOutlineNode createOutlineNode(Long projectId, CreateOutlineNodeCommand command, Long operatorId, String traceId) {
        log.info("创建大纲节点: projectId={}, title={}, parentId={}, operatorId={}", projectId, command.title(), command.parentId(), operatorId);
        getProject(projectId);
        NovelOutlineNode node = new NovelOutlineNode();
        node.setOutlineNodeId(businessIdGenerator.nextId());
        node.setProjectId(projectId);
        node.setParentId(command.parentId());
        node.setTitle(command.title());
        node.setNodeType(command.nodeType() == null ? "chapter" : command.nodeType());
        node.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
        node.setContent(command.content());
        int affected = novelGateway.insertOutlineNode(node);
        if (affected != 1) {
            log.error("创建大纲节点失败: projectId={}, title={}", projectId, command.title());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create outline node");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("nodeId", node.getOutlineNodeId());
        payload.put("parentId", node.getParentId());
        payload.put("title", node.getTitle());
        payload.put("nodeType", node.getNodeType());
        realtimeEventService.publishProjectEvent(projectId, "outline.node.created", payload);
         
        log.info("创建大纲节点成功: projectId={}, nodeId={}", projectId, node.getOutlineNodeId());
        return node;
    }

    /**
     * 更新大纲节点信息。
     *
     * @param projectId 入参：projectId
     * @param nodeId 入参：nodeId
     * @param command 入参：command
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelOutlineNode updateOutlineNode(Long projectId, Long nodeId, UpdateOutlineNodeCommand command, Long operatorId, String traceId) {
        log.info("更新大纲节点: projectId={}, nodeId={}, operatorId={}", projectId, nodeId, operatorId);
        NovelOutlineNode existing = novelGateway.findOutlineNodeByIdAndProjectId(projectId, nodeId);
        if (existing == null) {
            log.warn("更新大纲节点失败: projectId={}, nodeId={}, reason=not_found", projectId, nodeId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Outline node not found");
        }
        existing.setParentId(command.parentId());
        existing.setTitle(command.title());
        existing.setNodeType(command.nodeType() == null ? existing.getNodeType() : command.nodeType());
        existing.setSortOrder(command.sortOrder() == null ? existing.getSortOrder() : command.sortOrder());
        existing.setContent(command.content());
        int affected = novelGateway.updateOutlineNode(existing);
        if (affected != 1) {
            log.error("更新大纲节点失败: projectId={}, nodeId={}, reason=update_failed", projectId, nodeId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to update outline node");
        }
         
        log.info("更新大纲节点成功: projectId={}, nodeId={}", projectId, nodeId);
        return novelGateway.findOutlineNodeByIdAndProjectId(projectId, nodeId);
    }

    /**
     * 移动大纲节点位置。
     *
     * @param projectId 入参：projectId
     * @param nodeId 入参：nodeId
     * @param command 入参：command
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void moveOutlineNode(Long projectId, Long nodeId, MoveOutlineNodeCommand command, Long operatorId, String traceId) {
        log.info("移动大纲节点: projectId={}, nodeId={}, parentId={}, sortOrder={}, operatorId={}",
                projectId, nodeId, command.parentId(), command.sortOrder(), operatorId);
        int affected = novelGateway.moveOutlineNode(projectId, nodeId, command.parentId(), command.sortOrder() == null ? 0 : command.sortOrder());
        if (affected != 1) {
            log.warn("移动大纲节点失败: projectId={}, nodeId={}, reason=not_found", projectId, nodeId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Outline node not found");
        }
         
        log.info("移动大纲节点成功: projectId={}, nodeId={}", projectId, nodeId);
    }

    /**
     * 删除大纲节点（软删除）。
     *
     * @param projectId 入参：projectId
     * @param nodeId 入参：nodeId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void deleteOutlineNode(Long projectId, Long nodeId, Long operatorId, String traceId) {
        log.info("删除大纲节点: projectId={}, nodeId={}, operatorId={}", projectId, nodeId, operatorId);
        int affected = novelGateway.softDeleteOutlineNode(projectId, nodeId);
        if (affected != 1) {
            log.warn("删除大纲节点失败: projectId={}, nodeId={}, reason=not_found", projectId, nodeId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Outline node not found");
        }

        log.info("删除大纲节点成功: projectId={}, nodeId={}", projectId, nodeId);
    }
    private void applyDisplayNo(Long projectId, NovelChapter target) {
        List<NovelChapter> ordered = novelGateway.findChaptersByProjectId(projectId);
        for (int index = 0; index < ordered.size(); index++) {
            NovelChapter candidate = ordered.get(index);
            if (Objects.equals(candidate.getChapterId(), target.getChapterId())) {
                target.setDisplayNo(index + 1);
                return;
            }
        }
    }

    private void incrementStructureRevision(Long projectId) {
        if (novelGateway.incrementStructureRevision(projectId) != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.of(
                    "Failed to increment manuscript structure revision"
            );
        }
    }

}


