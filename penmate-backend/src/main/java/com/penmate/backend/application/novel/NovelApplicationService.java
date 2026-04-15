package com.penmate.backend.application.novel;

import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.model.NovelChapterVersion;
import com.penmate.backend.domain.novel.model.NovelCard;
import com.penmate.backend.domain.novel.model.NovelCardRelation;
import com.penmate.backend.domain.novel.model.NovelMember;
import com.penmate.backend.domain.novel.model.NovelOutlineNode;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.model.NovelVolume;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.shared.service.AuditService;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import com.penmate.backend.application.novel.command.NovelCommands.AddMemberCommand;
import com.penmate.backend.application.novel.command.NovelCommands.CommitChapterContentCommand;
import com.penmate.backend.application.novel.command.NovelCommands.CreateCardCommand;
import com.penmate.backend.application.novel.command.NovelCommands.CreateCardRelationCommand;
import com.penmate.backend.application.novel.command.NovelCommands.CreateChapterCommand;
import com.penmate.backend.application.novel.command.NovelCommands.CreateChapterVersionCommand;
import com.penmate.backend.application.novel.command.NovelCommands.CreateOutlineNodeCommand;
import com.penmate.backend.application.novel.command.NovelCommands.CreateProjectCommand;
import com.penmate.backend.application.novel.command.NovelCommands.CreateVolumeCommand;
import com.penmate.backend.application.novel.command.NovelCommands.MoveOutlineNodeCommand;
import com.penmate.backend.application.novel.command.NovelCommands.UpdateCardCommand;
import com.penmate.backend.application.novel.command.NovelCommands.UpdateChapterCommand;
import com.penmate.backend.application.novel.command.NovelCommands.UpdateMemberCommand;
import com.penmate.backend.application.novel.command.NovelCommands.UpdateOutlineNodeCommand;
import com.penmate.backend.application.novel.command.NovelCommands.UpdateProjectCommand;
import com.penmate.backend.application.novel.command.NovelCommands.UpdateVolumeCommand;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NovelApplicationService。
 * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
 */
@Service
public class NovelApplicationService {

    private final NovelGateway novelGateway;
    private final AuditService auditService;
    private final RealtimeEventService realtimeEventService;

    public NovelApplicationService(NovelGateway novelGateway,
                                   AuditService auditService,
                                   RealtimeEventService realtimeEventService) {
        this.novelGateway = novelGateway;
        this.auditService = auditService;
        this.realtimeEventService = realtimeEventService;
    }

    /**
     * 查询列表数据。
     *
     * @return 出参：处理结果
     */
    public List<NovelProject> listProjects() {
        return novelGateway.findAllProjects();
    }

    /**
     * 查询详情数据。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public NovelProject getProject(Long projectId) {
        NovelProject project = novelGateway.findProjectById(projectId);
        if (project == null) {
            throw new IllegalArgumentException("Project not found");
        }
        return project;
    }

    /**
     * 创建业务数据。
     *
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelProject createProject(CreateProjectCommand command, String traceId) {
        NovelProject project = new NovelProject();
        project.setOwnerUserId(command.ownerUserId());
        project.setTitle(command.title());
        project.setSummary(command.summary());
        project.setStatus(command.status() == null ? 1 : command.status());
        int affected = novelGateway.insertProject(project);
        if (affected != 1) {
            throw new IllegalArgumentException("Failed to create project");
        }
        writeAudit(traceId, command.ownerUserId(), "novel", "create-project", "novel_projects", String.valueOf(project.getId()), command.title(), 201);
        return project;
    }

    /**
     * 更新业务数据。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelProject updateProject(Long projectId, UpdateProjectCommand command, String traceId) {
        NovelProject existing = getProject(projectId);
        existing.setTitle(command.title());
        existing.setSummary(command.summary());
        existing.setStatus(command.status() == null ? existing.getStatus() : command.status());
        int affected = novelGateway.updateProject(existing);
        if (affected != 1) {
            throw new IllegalArgumentException("Failed to update project");
        }
        writeAudit(traceId, existing.getOwnerUserId(), "novel", "update-project", "novel_projects", String.valueOf(projectId), command.title(), 200);
        return getProject(projectId);
    }

    /**
     * 删除业务数据。
     *
     * @param projectId 入参：projectId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void deleteProject(Long projectId, Long operatorId, String traceId) {
        int affected = novelGateway.softDeleteProject(projectId);
        if (affected != 1) {
            throw new IllegalArgumentException("Project not found or already deleted");
        }
        writeAudit(traceId, operatorId, "novel", "delete-project", "novel_projects", String.valueOf(projectId), null, 200);
    }

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<NovelVolume> listVolumes(Long projectId) {
        return novelGateway.findVolumesByProjectId(projectId);
    }

    /**
     * 创建业务数据。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelVolume createVolume(Long projectId, CreateVolumeCommand command, Long operatorId, String traceId) {
        NovelVolume volume = new NovelVolume();
        volume.setProjectId(projectId);
        volume.setTitle(command.title());
        volume.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
        volume.setDescription(command.description());
        int affected = novelGateway.insertVolume(volume);
        if (affected != 1) {
            throw new IllegalArgumentException("Failed to create volume");
        }
        writeAudit(traceId, operatorId, "novel", "create-volume", "novel_volumes", String.valueOf(volume.getId()), command.title(), 201);
        return volume;
    }

    /**
     * 更新业务数据。
     *
     * @param projectId 入参：projectId
     * @param volumeId 入参：volumeId
     * @param command 入参：command
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelVolume updateVolume(Long projectId, Long volumeId, UpdateVolumeCommand command, Long operatorId, String traceId) {
        NovelVolume volume = new NovelVolume();
        volume.setId(volumeId);
        volume.setProjectId(projectId);
        volume.setTitle(command.title());
        volume.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
        volume.setDescription(command.description());
        int affected = novelGateway.updateVolume(volume);
        if (affected != 1) {
            throw new IllegalArgumentException("Volume not found or already deleted");
        }
        writeAudit(traceId, operatorId, "novel", "update-volume", "novel_volumes", String.valueOf(volumeId), command.title(), 200);
        return listVolumes(projectId).stream().filter(v -> volumeId.equals(v.getId())).findFirst().orElse(volume);
    }

    /**
     * 删除业务数据。
     *
     * @param projectId 入参：projectId
     * @param volumeId 入参：volumeId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void deleteVolume(Long projectId, Long volumeId, Long operatorId, String traceId) {
        int affected = novelGateway.softDeleteVolume(projectId, volumeId);
        if (affected != 1) {
            throw new IllegalArgumentException("Volume not found or already deleted");
        }
        writeAudit(traceId, operatorId, "novel", "delete-volume", "novel_volumes", String.valueOf(volumeId), null, 200);
    }

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<NovelChapter> listChapters(Long projectId) {
        return novelGateway.findChaptersByProjectId(projectId);
    }

    /**
     * 查询详情数据。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @return 出参：处理结果
     */
    public NovelChapter getChapter(Long projectId, Long chapterId) {
        NovelChapter chapter = novelGateway.findChapterByIdAndProjectId(projectId, chapterId);
        if (chapter == null) {
            throw new IllegalArgumentException("Chapter not found");
        }
        return chapter;
    }

    /**
     * 创建业务数据。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelChapter createChapter(Long projectId, CreateChapterCommand command, Long operatorId, String traceId) {
        NovelChapter chapter = new NovelChapter();
        chapter.setProjectId(projectId);
        chapter.setVolumeId(command.volumeId());
        chapter.setOutlineNodeId(command.outlineNodeId());
        chapter.setTitle(command.title());
        chapter.setChapterNo(command.chapterNo());
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
            throw new IllegalArgumentException("Failed to create chapter");
        }
        writeAudit(traceId, operatorId, "novel", "create-chapter", "novel_chapters", String.valueOf(chapter.getId()), command.title(), 201);
        return chapter;
    }

    /**
     * 更新业务数据。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param command 入参：command
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelChapter updateChapter(Long projectId, Long chapterId, UpdateChapterCommand command, Long operatorId, String traceId) {
        NovelChapter chapter = getChapter(projectId, chapterId);
        chapter.setVolumeId(command.volumeId());
        chapter.setOutlineNodeId(command.outlineNodeId());
        chapter.setTitle(command.title());
        chapter.setChapterNo(command.chapterNo());
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
            throw new IllegalArgumentException("Failed to update chapter");
        }
        writeAudit(traceId, operatorId, "novel", "update-chapter", "novel_chapters", String.valueOf(chapterId), command.title(), 200);
        return getChapter(projectId, chapterId);
    }

    /**
     * 删除业务数据。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void deleteChapter(Long projectId, Long chapterId, Long operatorId, String traceId) {
        int affected = novelGateway.softDeleteChapter(projectId, chapterId);
        if (affected != 1) {
            throw new IllegalArgumentException("Chapter not found or already deleted");
        }
        writeAudit(traceId, operatorId, "novel", "delete-chapter", "novel_chapters", String.valueOf(chapterId), null, 200);
    }

    /**
     * 发布业务状态。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void publishChapter(Long projectId, Long chapterId, Long operatorId, String traceId) {
        int affected = novelGateway.publishChapter(projectId, chapterId);
        if (affected != 1) {
            throw new IllegalArgumentException("Chapter not found or already deleted");
        }
        writeAudit(traceId, operatorId, "novel", "publish-chapter", "novel_chapters", String.valueOf(chapterId), null, 200);
    }

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<NovelMember> listMembers(Long projectId) {
        return novelGateway.findMembersByProjectId(projectId);
    }

    /**
     * 新增业务数据。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelMember addMember(Long projectId, AddMemberCommand command, Long operatorId, String traceId) {
        NovelMember member = new NovelMember();
        member.setProjectId(projectId);
        member.setUserId(command.userId());
        member.setMemberRole(command.memberRole());
        int affected = novelGateway.insertMember(member);
        if (affected != 1) {
            throw new IllegalArgumentException("Failed to add member");
        }
        writeAudit(traceId, operatorId, "novel", "add-member", "novel_members", projectId + ":" + command.userId(), command.memberRole(), 201);
        return listMembers(projectId).stream().filter(m -> command.userId().equals(m.getUserId())).findFirst().orElse(member);
    }

    /**
     * 更新业务数据。
     *
     * @param projectId 入参：projectId
     * @param userId 入参：userId
     * @param command 入参：command
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelMember updateMember(Long projectId, Long userId, UpdateMemberCommand command, Long operatorId, String traceId) {
        int affected = novelGateway.updateMemberRole(projectId, userId, command.memberRole());
        if (affected != 1) {
            throw new IllegalArgumentException("Member not found");
        }
        writeAudit(traceId, operatorId, "novel", "update-member", "novel_members", projectId + ":" + userId, command.memberRole(), 200);
        return listMembers(projectId).stream().filter(m -> userId.equals(m.getUserId())).findFirst().orElseThrow();
    }

    /**
     * 移除业务数据。
     *
     * @param projectId 入参：projectId
     * @param userId 入参：userId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void removeMember(Long projectId, Long userId, Long operatorId, String traceId) {
        int affected = novelGateway.deleteMember(projectId, userId);
        if (affected != 1) {
            throw new IllegalArgumentException("Member not found");
        }
        writeAudit(traceId, operatorId, "novel", "remove-member", "novel_members", projectId + ":" + userId, null, 200);
    }

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @return 出参：处理结果
     */
    public List<NovelChapterVersion> listChapterVersions(Long projectId, Long chapterId) {
        getChapter(projectId, chapterId);
        return novelGateway.findVersionsByChapterId(chapterId);
    }

    /**
     * 创建业务数据。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelChapterVersion createChapterVersion(Long projectId, Long chapterId, CreateChapterVersionCommand command, String traceId) {
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
            throw new IllegalArgumentException("Failed to create chapter version");
        }
        writeAudit(traceId, command.createdBy(), "novel", "create-chapter-version", "novel_chapter_versions", String.valueOf(version.getId()), command.changeType(), 201);
        return version;
    }

    /**
     * 查询详情数据。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param versionNo 入参：versionNo
     * @return 出参：处理结果
     */
    public NovelChapterVersion getChapterVersion(Long projectId, Long chapterId, Integer versionNo) {
        getChapter(projectId, chapterId);
        NovelChapterVersion version = novelGateway.findVersionByChapterAndVersion(chapterId, versionNo);
        if (version == null) {
            throw new IllegalArgumentException("Chapter version not found");
        }
        return version;
    }

    /**
     * 恢复历史数据。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param versionNo 入参：versionNo
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelChapter restoreChapterVersion(Long projectId, Long chapterId, Integer versionNo, Long operatorId, String traceId) {
        NovelChapterVersion version = getChapterVersion(projectId, chapterId, versionNo);
        // 复杂流程解析：恢复版本时仅回滚内容元数据，不覆盖章节业务字段，确保恢复行为可审计。
        int affected = novelGateway.updateChapterContentMeta(projectId, chapterId, version.getSnapshotObjectKey(), version.getSnapshotEtag(), version.getSnapshotSize(), version.getSnapshotChecksum(), "s3");
        if (affected != 1) {
            throw new IllegalArgumentException("Failed to restore chapter version");
        }
        writeAudit(traceId, operatorId, "novel", "restore-chapter-version", "novel_chapter_versions", version.getId().toString(), versionNo.toString(), 200);
        return getChapter(projectId, chapterId);
    }

    /**
     * 查询详情数据。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @return 出参：处理结果
     */
    public Map<String, String> getChapterContentUrl(Long projectId, Long chapterId) {
        NovelChapter chapter = getChapter(projectId, chapterId);
        return Map.of("url", "https://object.local/read/" + chapter.getContentObjectKey());
    }

    /**
     * 查询详情数据。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @return 出参：处理结果
     */
    public Map<String, String> getChapterContentUploadUrl(Long projectId, Long chapterId) {
        getChapter(projectId, chapterId);
        String objectKey = "novels/" + projectId + "/chapters/" + chapterId + "/" + UUID.randomUUID() + ".md";
        return Map.of(
                "objectKey", objectKey,
                "uploadUrl", "https://object.local/upload/" + objectKey + "?token=" + UUID.randomUUID()
        );
    }

    /**
     * 提交业务变更。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param command 入参：command
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelChapter commitChapterContent(Long projectId, Long chapterId, CommitChapterContentCommand command, Long operatorId, String traceId) {
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
            throw new IllegalArgumentException("Failed to commit chapter content");
        }
        writeAudit(traceId, operatorId, "novel", "commit-chapter-content", "novel_chapters", String.valueOf(chapterId), command.objectKey(), 200);
        return getChapter(projectId, chapterId);
    }

    /**
     * 查询详情数据。
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param versionNo 入参：versionNo
     * @return 出参：处理结果
     */
    public Map<String, String> getChapterVersionSnapshotUrl(Long projectId, Long chapterId, Integer versionNo) {
        NovelChapterVersion version = getChapterVersion(projectId, chapterId, versionNo);
        return Map.of("url", "https://object.local/read/" + version.getSnapshotObjectKey());
    }

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<NovelOutlineNode> listOutlineTree(Long projectId) {
        getProject(projectId);
        return novelGateway.findOutlineNodesByProjectId(projectId);
    }

    /**
     * 创建业务数据。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelOutlineNode createOutlineNode(Long projectId, CreateOutlineNodeCommand command, Long operatorId, String traceId) {
        getProject(projectId);
        NovelOutlineNode node = new NovelOutlineNode();
        node.setProjectId(projectId);
        node.setParentId(command.parentId());
        node.setTitle(command.title());
        node.setNodeType(command.nodeType() == null ? "chapter" : command.nodeType());
        node.setSortOrder(command.sortOrder() == null ? 0 : command.sortOrder());
        node.setContent(command.content());
        int affected = novelGateway.insertOutlineNode(node);
        if (affected != 1) {
            throw new IllegalArgumentException("Failed to create outline node");
        }
        realtimeEventService.publishProjectEvent(projectId, "outline.node.created", Map.of(
                "nodeId", node.getId(),
                "parentId", node.getParentId(),
                "title", node.getTitle(),
                "nodeType", node.getNodeType()
        ));
        writeAudit(traceId, operatorId, "novel", "create-outline-node", "novel_outline_nodes", String.valueOf(node.getId()), command.title(), 201);
        return node;
    }

    /**
     * 更新业务数据。
     *
     * @param projectId 入参：projectId
     * @param nodeId 入参：nodeId
     * @param command 入参：command
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelOutlineNode updateOutlineNode(Long projectId, Long nodeId, UpdateOutlineNodeCommand command, Long operatorId, String traceId) {
        NovelOutlineNode existing = novelGateway.findOutlineNodeByIdAndProjectId(projectId, nodeId);
        if (existing == null) {
            throw new IllegalArgumentException("Outline node not found");
        }
        existing.setParentId(command.parentId());
        existing.setTitle(command.title());
        existing.setNodeType(command.nodeType() == null ? existing.getNodeType() : command.nodeType());
        existing.setSortOrder(command.sortOrder() == null ? existing.getSortOrder() : command.sortOrder());
        existing.setContent(command.content());
        int affected = novelGateway.updateOutlineNode(existing);
        if (affected != 1) {
            throw new IllegalArgumentException("Failed to update outline node");
        }
        writeAudit(traceId, operatorId, "novel", "update-outline-node", "novel_outline_nodes", String.valueOf(nodeId), command.title(), 200);
        return novelGateway.findOutlineNodeByIdAndProjectId(projectId, nodeId);
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param nodeId 入参：nodeId
     * @param command 入参：command
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void moveOutlineNode(Long projectId, Long nodeId, MoveOutlineNodeCommand command, Long operatorId, String traceId) {
        int affected = novelGateway.moveOutlineNode(projectId, nodeId, command.parentId(), command.sortOrder() == null ? 0 : command.sortOrder());
        if (affected != 1) {
            throw new IllegalArgumentException("Outline node not found");
        }
        writeAudit(traceId, operatorId, "novel", "move-outline-node", "novel_outline_nodes", String.valueOf(nodeId), null, 200);
    }

    /**
     * 删除业务数据。
     *
     * @param projectId 入参：projectId
     * @param nodeId 入参：nodeId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void deleteOutlineNode(Long projectId, Long nodeId, Long operatorId, String traceId) {
        int affected = novelGateway.softDeleteOutlineNode(projectId, nodeId);
        if (affected != 1) {
            throw new IllegalArgumentException("Outline node not found");
        }
        writeAudit(traceId, operatorId, "novel", "delete-outline-node", "novel_outline_nodes", String.valueOf(nodeId), null, 200);
    }

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<NovelCard> listCards(Long projectId) {
        getProject(projectId);
        return novelGateway.findCardsByProjectId(projectId);
    }

    /**
     * 查询详情数据。
     *
     * @param projectId 入参：projectId
     * @param cardId 入参：cardId
     * @return 出参：处理结果
     */
    public NovelCard getCard(Long projectId, Long cardId) {
        NovelCard card = novelGateway.findCardByIdAndProjectId(projectId, cardId);
        if (card == null) {
            throw new IllegalArgumentException("Card not found");
        }
        return card;
    }

    /**
     * 创建业务数据。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelCard createCard(Long projectId, CreateCardCommand command, Long operatorId, String traceId) {
        getProject(projectId);
        NovelCard card = new NovelCard();
        card.setProjectId(projectId);
        card.setCardType(command.cardType());
        card.setName(command.name());
        card.setSummary(command.summary());
        card.setDetailJson(command.detailJson());
        int affected = novelGateway.insertCard(card);
        if (affected != 1) {
            throw new IllegalArgumentException("Failed to create card");
        }
        writeAudit(traceId, operatorId, "novel", "create-card", "novel_cards", String.valueOf(card.getId()), command.name(), 201);
        return card;
    }

    /**
     * 更新业务数据。
     *
     * @param projectId 入参：projectId
     * @param cardId 入参：cardId
     * @param command 入参：command
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelCard updateCard(Long projectId, Long cardId, UpdateCardCommand command, Long operatorId, String traceId) {
        NovelCard card = getCard(projectId, cardId);
        card.setCardType(command.cardType() == null ? card.getCardType() : command.cardType());
        card.setName(command.name());
        card.setSummary(command.summary());
        card.setDetailJson(command.detailJson());
        int affected = novelGateway.updateCard(card);
        if (affected != 1) {
            throw new IllegalArgumentException("Failed to update card");
        }
        realtimeEventService.publishProjectEvent(projectId, "card.updated", Map.of(
                "cardId", cardId,
                "cardType", card.getCardType(),
                "name", card.getName(),
                "summary", card.getSummary()
        ));
        writeAudit(traceId, operatorId, "novel", "update-card", "novel_cards", String.valueOf(cardId), command.name(), 200);
        return getCard(projectId, cardId);
    }

    /**
     * 删除业务数据。
     *
     * @param projectId 入参：projectId
     * @param cardId 入参：cardId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void deleteCard(Long projectId, Long cardId, Long operatorId, String traceId) {
        int affected = novelGateway.softDeleteCard(projectId, cardId);
        if (affected != 1) {
            throw new IllegalArgumentException("Card not found");
        }
        writeAudit(traceId, operatorId, "novel", "delete-card", "novel_cards", String.valueOf(cardId), null, 200);
    }

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<NovelCardRelation> listCardRelations(Long projectId) {
        getProject(projectId);
        return novelGateway.findCardRelationsByProjectId(projectId);
    }

    /**
     * 创建业务数据。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public NovelCardRelation createCardRelation(Long projectId, CreateCardRelationCommand command, Long operatorId, String traceId) {
        getCard(projectId, command.fromCardId());
        getCard(projectId, command.toCardId());
        NovelCardRelation relation = new NovelCardRelation();
        relation.setProjectId(projectId);
        relation.setFromCardId(command.fromCardId());
        relation.setToCardId(command.toCardId());
        relation.setRelationType(command.relationType());
        relation.setDescription(command.description());
        int affected = novelGateway.insertCardRelation(relation);
        if (affected != 1) {
            throw new IllegalArgumentException("Failed to create card relation");
        }
        writeAudit(traceId, operatorId, "novel", "create-card-relation", "novel_card_relations", String.valueOf(relation.getId()), command.relationType(), 201);
        return relation;
    }

    /**
     * 删除业务数据。
     *
     * @param projectId 入参：projectId
     * @param relationId 入参：relationId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void deleteCardRelation(Long projectId, Long relationId, Long operatorId, String traceId) {
        int affected = novelGateway.softDeleteCardRelation(projectId, relationId);
        if (affected != 1) {
            throw new IllegalArgumentException("Card relation not found");
        }
        writeAudit(traceId, operatorId, "novel", "delete-card-relation", "novel_card_relations", String.valueOf(relationId), null, 200);
    }

    private void writeAudit(String traceId,
                            Long userId,
                            String module,
                            String action,
                            String resourceType,
                            String resourceId,
                            String requestJson,
                            int responseCode) {
        String finalTraceId = (traceId == null || traceId.isBlank()) ? UUID.randomUUID().toString() : traceId;
        auditService.write(finalTraceId, userId, module, action, resourceType, resourceId, requestJson, responseCode);
    }
}

