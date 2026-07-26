package com.penmate.backend.application.novel;

import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.model.ChapterAiUndoOperation;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.model.NovelVolume;
import com.penmate.backend.domain.novel.repository.NovelGateway;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.application.storybible.StoryBibleApplicationService;
import com.penmate.backend.application.rag.ProjectAiConfigurationService;
import com.penmate.backend.application.novel.command.NovelCommands.CreateChapterCommand;
import com.penmate.backend.application.novel.command.NovelCommands.CreateProjectCommand;
import com.penmate.backend.application.novel.command.NovelCommands.CreateVolumeCommand;
import com.penmate.backend.application.novel.command.NovelCommands.ImportChapterCommand;
import com.penmate.backend.application.novel.command.NovelCommands.ImportProjectCommand;
import com.penmate.backend.application.novel.command.NovelCommands.ImportVolumeCommand;
import com.penmate.backend.application.novel.command.NovelCommands.DirectoryNodeType;
import com.penmate.backend.application.novel.command.NovelCommands.MoveDirectoryItemCommand;
import com.penmate.backend.application.novel.command.NovelCommands.UpdateChapterCommand;
import com.penmate.backend.application.novel.command.NovelCommands.UpdateProjectCommand;
import com.penmate.backend.application.novel.command.NovelCommands.UpdateVolumeCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 小说项目应用服务。
 * <p>负责项目、卷章、成员、大纲、卡片与章节版本的全链路业务编排，并发布实时事件与审计日志。</p>
 */
@Service
@Slf4j
public class NovelApplicationService {

    private static final long AI_CHAPTER_LEASE_SECONDS = 300;
    private static final long AI_UNDO_RETENTION_SECONDS = 24 * 60 * 60;
    private static final Set<String> PROJECT_GENRES = Set.of(
            "玄幻", "奇幻", "武侠", "仙侠", "都市", "历史", "科幻", "悬疑", "言情", "现实", "轻小说", "其他"
    );

    private final NovelGateway novelGateway;
    private final BusinessIdGenerator businessIdGenerator;
    private final StoryBibleApplicationService storyBibleApplicationService;
    private final ProjectAiConfigurationService projectAiConfigurationService;

    public NovelApplicationService(NovelGateway novelGateway,
                                   BusinessIdGenerator businessIdGenerator,
                                   StoryBibleApplicationService storyBibleApplicationService,
                                   ProjectAiConfigurationService projectAiConfigurationService) {
        this.novelGateway = novelGateway;
        this.businessIdGenerator = businessIdGenerator;
        this.storyBibleApplicationService = storyBibleApplicationService;
        this.projectAiConfigurationService = projectAiConfigurationService;
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

    public List<NovelProject> listProjects(Long ownerUserId) {
        Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        return novelGateway.findAllProjects().stream()
                .filter(project -> Objects.equals(project.getOwnerUserId(), ownerUserId))
                .toList();
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
        NovelProject project = insertProject(command);

        NovelVolume firstVolume = insertVolume(project.getProjectId(), "第一卷", 1);
        insertChapter(project.getProjectId(), firstVolume.getVolumeId(), "第一章", "", 1);

        initializeProject(project, command.ownerUserId());
        log.info("创建小说项目成功: projectId={}, ownerUserId={}", project.getProjectId(), command.ownerUserId());
        return project;
    }

    @Transactional
    public NovelProject createImportedProject(ImportProjectCommand command, String traceId) {
        if (command == null || command.project() == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.badRequest("Import project is required");
        }
        List<ImportVolumeCommand> volumes = command.volumes() == null ? List.of() : command.volumes();
        if (volumes.isEmpty()) {
            throw com.penmate.backend.application.common.exception.BusinessException.badRequest("Import requires at least one volume");
        }
        NovelProject project = insertProject(command.project());
        int chapterCount = 0;
        for (int volumeIndex = 0; volumeIndex < volumes.size(); volumeIndex++) {
            ImportVolumeCommand sourceVolume = volumes.get(volumeIndex);
            String volumeTitle = requireImportTitle(sourceVolume == null ? null : sourceVolume.title(), "Volume title");
            NovelVolume volume = insertVolume(project.getProjectId(), volumeTitle, volumeIndex + 1);
            List<ImportChapterCommand> chapters = sourceVolume.chapters() == null ? List.of() : sourceVolume.chapters();
            if (chapters.isEmpty()) {
                throw com.penmate.backend.application.common.exception.BusinessException.badRequest("Every imported volume requires a chapter");
            }
            for (int chapterIndex = 0; chapterIndex < chapters.size(); chapterIndex++) {
                ImportChapterCommand sourceChapter = chapters.get(chapterIndex);
                String chapterTitle = requireImportTitle(sourceChapter == null ? null : sourceChapter.title(), "Chapter title");
                insertChapter(project.getProjectId(), volume.getVolumeId(), chapterTitle,
                        sourceChapter.content() == null ? "" : sourceChapter.content(), chapterIndex + 1);
                chapterCount++;
            }
        }
        if (chapterCount > 2000) {
            throw com.penmate.backend.application.common.exception.BusinessException.badRequest("Import has too many chapters");
        }
        initializeProject(project, command.project().ownerUserId());
        log.info("导入小说项目成功: projectId={}, volumeCount={}, chapterCount={}",
                project.getProjectId(), volumes.size(), chapterCount);
        return project;
    }

    private NovelProject insertProject(CreateProjectCommand command) {
        NovelProject project = new NovelProject();
        project.setProjectId(businessIdGenerator.nextId());
        project.setOwnerUserId(command.ownerUserId());
        applyProjectMetadata(project, command.title(), command.summary(), command.genre(), command.customGenre(), command.tags());
        project.setStatus(command.status() == null ? 1 : command.status());
        project.setStructureRevision(1L);
        int affected = novelGateway.insertProject(project);
        if (affected != 1) {
            log.error("创建小说项目失败: ownerUserId={}, title={}", command.ownerUserId(), command.title());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create project");
        }
        return project;
    }

    private NovelVolume insertVolume(Long projectId, String title, int sortOrder) {
        NovelVolume volume = new NovelVolume();
        volume.setVolumeId(businessIdGenerator.nextId());
        volume.setProjectId(projectId);
        volume.setTitle(title);
        volume.setSortOrder(sortOrder);
        if (novelGateway.insertVolume(volume) != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create imported volume");
        }
        return volume;
    }

    private void insertChapter(Long projectId, Long volumeId, String title, String content, int sortOrder) {
        NovelChapter chapter = new NovelChapter();
        chapter.setChapterId(businessIdGenerator.nextId());
        chapter.setProjectId(projectId);
        chapter.setVolumeId(volumeId);
        chapter.setTitle(title);
        chapter.setSortOrder(sortOrder);
        chapter.setContent(content);
        chapter.setWordCount(countWords(content));
        if (novelGateway.insertChapter(chapter) != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create imported chapter");
        }
    }

    private String requireImportTitle(String title, String field) {
        String normalized = title == null ? "" : title.trim();
        if (normalized.isEmpty() || normalized.length() > 200) {
            throw com.penmate.backend.application.common.exception.BusinessException.badRequest(field + " must contain 1 to 200 characters");
        }
        return normalized;
    }

    private void initializeProject(NovelProject project, Long ownerUserId) {
        storyBibleApplicationService.bootstrap(
                project.getProjectId(),
                project.getTitle(),
                ownerUserId
        );
        projectAiConfigurationService.initializeProject(project.getProjectId(), ownerUserId);
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
        applyProjectMetadata(existing, command.title(), command.summary(), command.genre(), command.customGenre(), command.tags());
        existing.setStatus(command.status() == null ? existing.getStatus() : command.status());
        int affected = novelGateway.updateProject(existing);
        if (affected != 1) {
            log.error("更新小说项目失败: projectId={}", projectId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to update project");
        }
         
        log.info("更新小说项目成功: projectId={}", projectId);
        return getProject(projectId);
    }

    private void applyProjectMetadata(NovelProject project, String title, String summary, String genre,
                                      String customGenre, List<String> tags) {
        if (title == null || title.isBlank()) {
            throw com.penmate.backend.application.common.exception.BusinessException.badRequest("Project title is required");
        }
        String normalizedGenre = genre == null || genre.isBlank() ? "其他" : genre.trim();
        if (!PROJECT_GENRES.contains(normalizedGenre)) {
            throw com.penmate.backend.application.common.exception.BusinessException.badRequest("Unsupported project genre");
        }
        String normalizedCustomGenre = customGenre == null ? null : customGenre.trim();
        if ("其他".equals(normalizedGenre) && (normalizedCustomGenre == null || normalizedCustomGenre.isBlank())) {
            normalizedCustomGenre = null;
        }
        if (normalizedCustomGenre != null && normalizedCustomGenre.length() > 40) {
            throw com.penmate.backend.application.common.exception.BusinessException.badRequest("Custom genre is too long");
        }
        LinkedHashSet<String> normalizedTags = new LinkedHashSet<>();
        if (tags != null) {
            for (String tag : tags) {
                if (tag == null || tag.isBlank()) continue;
                String normalized = tag.trim();
                if (normalized.length() > 12) {
                    throw com.penmate.backend.application.common.exception.BusinessException.badRequest("Project tag is too long");
                }
                normalizedTags.add(normalized);
            }
        }
        if (normalizedTags.size() > 10) {
            throw com.penmate.backend.application.common.exception.BusinessException.badRequest("A project can have at most 10 tags");
        }
        project.setTitle(title.trim());
        project.setSummary(summary == null ? null : summary.trim());
        project.setGenre(normalizedGenre);
        project.setCustomGenre("其他".equals(normalizedGenre) ? normalizedCustomGenre : null);
        project.setTags(List.copyOf(normalizedTags));
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
        int affected = novelGateway.softDeleteProject(projectId, operatorId);
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

    public NovelDirectoryView getDirectory(Long projectId, Long actorUserId) {
        NovelProject project = requireOwnedProject(projectId, actorUserId);
        return directoryView(projectId, project.getStructureRevision());
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
        List<NovelVolume> volumes = new ArrayList<>(listVolumes(projectId));
        NovelVolume existing = volumes.stream()
                .filter(item -> volumeId.equals(item.getVolumeId()))
                .findFirst()
                .orElseThrow(() -> com.penmate.backend.application.common.exception.BusinessException.of(
                        "Volume not found or already deleted"));
        existing.setTitle(command.title());
        existing.setDescription(command.description());
        int currentIndex = volumes.indexOf(existing);
        int targetIndex = Math.max(0, Math.min(volumes.size() - 1,
                (command.sortOrder() == null ? currentIndex + 1 : command.sortOrder()) - 1));
        volumes.remove(currentIndex);
        volumes.add(targetIndex, existing);
        for (int index = 0; index < volumes.size(); index++) {
            NovelVolume volume = volumes.get(index);
            volume.setSortOrder(index + 1);
            if (novelGateway.updateVolume(volume) != 1) {
                throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to reorder volumes");
            }
        }
        if (currentIndex != targetIndex) {
            incrementStructureRevision(projectId);
        }
         
        log.info("更新分卷成功: projectId={}, volumeId={}", projectId, volumeId);
        return listVolumes(projectId).stream().filter(v -> volumeId.equals(v.getVolumeId())).findFirst().orElse(existing);
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
        rejectActiveAiLeaseInVolume(projectId, volumeId);
        novelGateway.softDeleteChaptersByVolume(projectId, volumeId);
        rejectActiveAiLeaseInVolume(projectId, volumeId);
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

    @Transactional
    public AiChapterLeaseView acquireChapterAiLease(Long projectId, Long chapterId, Long actorUserId, Long runId) {
        requireOwnedProject(projectId, actorUserId);
        if (runId == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.badRequest("Run id is required");
        }
        NovelChapter current = getChapter(projectId, chapterId);
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(AI_CHAPTER_LEASE_SECONDS);
        int affected = novelGateway.acquireChapterAiLease(projectId, chapterId, runId, token, expiresAt);
        if (affected == 1) {
            NovelChapter acquired = getChapter(projectId, chapterId);
            return new AiChapterLeaseView(true, token, acquired.getLeaseExpiresAt(),
                    acquired.getContentRevision(), value(acquired.getContent()), null);
        }
        return new AiChapterLeaseView(false, null, current.getLeaseExpiresAt(),
                current.getContentRevision(), value(current.getContent()), "Chapter is already being edited by AI");
    }

    @Transactional
    public void renewChapterAiLease(Long projectId, Long chapterId, Long actorUserId, String leaseToken) {
        requireOwnedProject(projectId, actorUserId);
        Instant expiresAt = Instant.now().plusSeconds(AI_CHAPTER_LEASE_SECONDS);
        if (novelGateway.renewChapterAiLease(projectId, chapterId, requireLeaseToken(leaseToken), expiresAt) != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.conflict("AI chapter lease has expired");
        }
    }

    @Transactional
    public void releaseChapterAiLease(Long projectId, Long chapterId, Long actorUserId, String leaseToken) {
        requireOwnedProject(projectId, actorUserId);
        novelGateway.releaseChapterAiLease(projectId, chapterId, requireLeaseToken(leaseToken));
    }

    @Transactional
    public NovelChapter saveChapterContent(Long projectId, Long chapterId, Long actorUserId,
                                           Long expectedRevision, String content) {
        requireOwnedProject(projectId, actorUserId);
        if (expectedRevision == null || expectedRevision < 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.badRequest("Expected revision is required");
        }
        String normalizedContent = value(content);
        int wordCount = normalizedContent.codePoints().filter(codePoint -> !Character.isWhitespace(codePoint)).toArray().length;
        int affected = novelGateway.updateUserChapterContent(projectId, chapterId,
                expectedRevision, normalizedContent, wordCount);
        if (affected != 1) {
            NovelChapter current = getChapter(projectId, chapterId);
            rejectActiveAiLease(current);
            throw com.penmate.backend.application.common.exception.BusinessException.of(
                    com.penmate.backend.application.common.exception.BusinessErrorType.CONFLICT,
                    "CHAPTER_REVISION_CONFLICT",
                    "Chapter was updated in another page",
                    Map.of("contentRevision", current.getContentRevision()));
        }
        novelGateway.invalidateAvailableAiUndoByChapter(projectId, chapterId);
        return getChapter(projectId, chapterId);
    }

    @Transactional
    public AiChapterEditResult saveAiChapterEdit(Long projectId, Long chapterId, Long actorUserId,
                                                 Long runId, String toolCallId, String leaseToken,
                                                 Long expectedRevision, String content) {
        requireOwnedProject(projectId, actorUserId);
        NovelChapter current = novelGateway.findChapterByIdAndProjectId(projectId, chapterId);
        if (current == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.notFound("Chapter not found");
        }
        if (!Objects.equals(current.getContentRevision(), expectedRevision)) {
            throw com.penmate.backend.application.common.exception.BusinessException.conflict("Chapter changed before AI commit");
        }
        String normalizedContent = value(content);
        int wordCount = countWords(normalizedContent);
        int affected = novelGateway.updateAiChapterContent(projectId, chapterId, requireLeaseToken(leaseToken),
                expectedRevision, normalizedContent, wordCount);
        if (affected != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.conflict(
                    "Chapter changed or the AI editing lease is no longer valid");
        }

        String currentHash = sha256(value(current.getContent()));
        String resultHash = sha256(normalizedContent);
        ChapterAiUndoOperation operation = novelGateway.findAvailableAiUndoByRunAndChapter(projectId, runId, chapterId);
        if (operation != null && !Objects.equals(operation.getResultContentHash(), currentHash)) {
            operation = null;
        }
        Instant createdAt = Instant.now();
        Instant expiresAt = createdAt.plusSeconds(AI_UNDO_RETENTION_SECONDS);
        if (operation == null) {
            operation = new ChapterAiUndoOperation();
            operation.setOperationId(businessIdGenerator.nextId());
            operation.setProjectId(projectId);
            operation.setChapterId(chapterId);
            operation.setRunId(runId);
            operation.setToolCallId(toolCallId);
            operation.setBeforeContent(value(current.getContent()));
            operation.setBeforeWordCount(current.getWordCount() == null ? countWords(value(current.getContent())) : current.getWordCount());
            operation.setResultContentHash(resultHash);
            operation.setSequenceNo(novelGateway.nextAiUndoSequence(projectId, chapterId));
            operation.setAppliedRevision(expectedRevision + 1);
            operation.setStatus("AVAILABLE");
            operation.setCreatedAt(createdAt);
            operation.setExpiresAt(expiresAt);
            if (novelGateway.insertAiUndo(operation) != 1) {
                throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to save AI undo operation");
            }
        } else {
            operation.setToolCallId(toolCallId);
            operation.setResultContentHash(resultHash);
            operation.setAppliedRevision(expectedRevision + 1);
            operation.setExpiresAt(expiresAt);
            if (novelGateway.updateMergedAiUndo(operation) != 1) {
                throw com.penmate.backend.application.common.exception.BusinessException.conflict("AI undo operation is no longer available");
            }
        }
        NovelChapter saved = getChapter(projectId, chapterId);
        return new AiChapterEditResult(saved, toUndoView(operation, saved.getTitle()));
    }

    public List<AiUndoView> listAvailableAiUndoForChapter(Long projectId, Long chapterId, Long actorUserId) {
        requireOwnedProject(projectId, actorUserId);
        String title = getChapter(projectId, chapterId).getTitle();
        return novelGateway.listAvailableAiUndoByChapter(projectId, chapterId).stream()
                .map(operation -> toUndoView(operation, title))
                .toList();
    }

    public List<AiUndoView> listAvailableAiUndoForProject(Long projectId, Long actorUserId) {
        requireOwnedProject(projectId, actorUserId);
        Map<Long, String> chapterTitles = novelGateway.findChaptersByProjectId(projectId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        NovelChapter::getChapterId, NovelChapter::getTitle, (left, right) -> left));
        return novelGateway.listAvailableAiUndoByProject(projectId).stream()
                .map(operation -> toUndoView(operation,
                        chapterTitles.getOrDefault(operation.getChapterId(), "未命名章节")))
                .toList();
    }

    @Transactional
    public AiUndoDismissResult dismissAiUndo(Long projectId, List<Long> operationIds, Long actorUserId) {
        requireOwnedProject(projectId, actorUserId);
        if (operationIds == null || operationIds.isEmpty()) {
            throw com.penmate.backend.application.common.exception.BusinessException.badRequest(
                    "At least one AI undo operation is required");
        }

        Map<Long, Long> dismissThroughByChapter = new java.util.LinkedHashMap<>();
        for (Long operationId : new LinkedHashSet<>(operationIds)) {
            if (operationId == null) continue;
            ChapterAiUndoOperation operation = novelGateway.findAiUndoByOperationId(projectId, operationId);
            if (operation == null || !"AVAILABLE".equals(operation.getStatus())
                    || operation.getExpiresAt() == null || !operation.getExpiresAt().isAfter(Instant.now())) {
                continue;
            }
            dismissThroughByChapter.merge(operation.getChapterId(), operation.getSequenceNo(), Math::max);
        }

        List<Long> dismissedOperationIds = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : dismissThroughByChapter.entrySet()) {
            Long chapterId = entry.getKey();
            Long sequenceNo = entry.getValue();
            novelGateway.listAvailableAiUndoByChapter(projectId, chapterId).stream()
                    .filter(operation -> operation.getSequenceNo() <= sequenceNo)
                    .map(ChapterAiUndoOperation::getOperationId)
                    .forEach(dismissedOperationIds::add);
            novelGateway.dismissAvailableAiUndoThrough(projectId, chapterId, sequenceNo);
        }
        return new AiUndoDismissResult(List.copyOf(dismissedOperationIds));
    }

    @Transactional
    public AiUndoView undoAiChapterEdit(Long projectId, Long operationId, Long actorUserId) {
        requireOwnedProject(projectId, actorUserId);
        ChapterAiUndoOperation operation = novelGateway.findAiUndoByOperationId(projectId, operationId);
        if (operation == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.notFound("AI edit operation not found");
        }
        return restoreAiOperation(operation);
    }

    @Transactional
    public List<AiUndoView> undoAiChapterEditsForRun(Long projectId, Long runId, Long actorUserId) {
        requireOwnedProject(projectId, actorUserId);
        List<ChapterAiUndoOperation> operations = novelGateway.listAvailableAiUndoByRun(projectId, runId);
        if (operations.isEmpty()) {
            throw com.penmate.backend.application.common.exception.BusinessException.conflict("No AI edits from this run can be undone");
        }
        for (ChapterAiUndoOperation operation : operations) {
            assertLatestUndoOperation(operation);
            assertUndoMatchesCurrentChapter(operation);
        }
        List<AiUndoView> restored = new ArrayList<>();
        for (ChapterAiUndoOperation operation : operations) {
            restored.add(restoreAiOperation(operation));
        }
        return List.copyOf(restored);
    }

    private AiUndoView restoreAiOperation(ChapterAiUndoOperation operation) {
        if (!"AVAILABLE".equals(operation.getStatus()) || operation.getExpiresAt() == null
                || !operation.getExpiresAt().isAfter(Instant.now())) {
            throw com.penmate.backend.application.common.exception.BusinessException.conflict("AI edit can no longer be undone");
        }
        assertLatestUndoOperation(operation);
        NovelChapter current = assertUndoMatchesCurrentChapter(operation);
        int affected = novelGateway.restoreAiChapterContent(operation.getProjectId(), operation.getChapterId(),
                current.getContentRevision(), value(current.getContent()), operation.getBeforeContent(),
                operation.getBeforeWordCount());
        if (affected != 1 || novelGateway.markAiUndoUndone(operation.getOperationId()) != 1) {
            throw com.penmate.backend.application.common.exception.BusinessException.conflict(
                    "Chapter changed while the AI edit was being undone");
        }
        List<ChapterAiUndoOperation> remaining = novelGateway.listAvailableAiUndoByChapter(
                operation.getProjectId(), operation.getChapterId());
        if (!remaining.isEmpty()) {
            novelGateway.rebaseAiUndoRevision(remaining.getFirst().getOperationId(), current.getContentRevision() + 1);
        }
        operation.setStatus("UNDONE");
        operation.setUndoneAt(Instant.now());
        return toUndoView(operation, current.getTitle());
    }

    private void assertLatestUndoOperation(ChapterAiUndoOperation operation) {
        List<ChapterAiUndoOperation> stack = novelGateway.listAvailableAiUndoByChapter(
                operation.getProjectId(), operation.getChapterId());
        if (stack.isEmpty() || !Objects.equals(stack.getFirst().getOperationId(), operation.getOperationId())) {
            throw com.penmate.backend.application.common.exception.BusinessException.conflict(
                    "A newer AI edit must be undone first");
        }
    }

    private NovelChapter assertUndoMatchesCurrentChapter(ChapterAiUndoOperation operation) {
        NovelChapter current = novelGateway.findChapterByIdAndProjectId(operation.getProjectId(), operation.getChapterId());
        if (current == null || !Objects.equals(current.getContentRevision(), operation.getAppliedRevision())
                || !Objects.equals(sha256(value(current.getContent())), operation.getResultContentHash())) {
            throw com.penmate.backend.application.common.exception.BusinessException.conflict(
                    "Chapter was changed after the AI edit");
        }
        return current;
    }

    private AiUndoView toUndoView(ChapterAiUndoOperation operation, String chapterTitle) {
        return new AiUndoView(operation.getOperationId(), operation.getRunId(), operation.getChapterId(), chapterTitle,
                operation.getStatus(), operation.getSequenceNo(), operation.getCreatedAt(), operation.getExpiresAt(),
                operation.getUndoneAt());
    }

    private int countWords(String content) {
        return value(content).codePoints().filter(codePoint -> !Character.isWhitespace(codePoint)).toArray().length;
    }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value(content).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private NovelProject requireOwnedProject(Long projectId, Long actorUserId) {
        NovelProject project = getProject(projectId);
        if (!Objects.equals(project.getOwnerUserId(), actorUserId)) {
            throw com.penmate.backend.application.common.exception.BusinessException.notFound("Project not found");
        }
        return project;
    }

    private String requireLeaseToken(String leaseToken) {
        if (leaseToken == null || leaseToken.isBlank()) {
            throw com.penmate.backend.application.common.exception.BusinessException.badRequest("Lease token is required");
        }
        return leaseToken.trim();
    }

    private String value(String content) {
        return content == null ? "" : content;
    }

    private boolean hasActiveAiLease(NovelChapter chapter) {
        return chapter != null
                && "AI".equals(chapter.getLeaseOwnerType())
                && chapter.getLeaseExpiresAt() != null
                && chapter.getLeaseExpiresAt().isAfter(Instant.now());
    }

    private void rejectActiveAiLease(NovelChapter chapter) {
        if (!hasActiveAiLease(chapter)) return;
        throw com.penmate.backend.application.common.exception.BusinessException.of(
                com.penmate.backend.application.common.exception.BusinessErrorType.CONFLICT,
                "CHAPTER_AI_EDITING",
                "AI is editing this chapter",
                Map.of("contentRevision", chapter.getContentRevision(),
                        "leaseExpiresAt", chapter.getLeaseExpiresAt()));
    }

    private void rejectActiveAiLeaseInVolume(Long projectId, Long volumeId) {
        if (novelGateway.countActiveAiChapterLeasesByVolume(projectId, volumeId) <= 0) return;
        throw com.penmate.backend.application.common.exception.BusinessException.of(
                com.penmate.backend.application.common.exception.BusinessErrorType.CONFLICT,
                "CHAPTER_AI_EDITING",
                "AI is editing a chapter in this volume",
                Map.of("volumeId", volumeId));
    }

    public record AiChapterLeaseView(boolean editable, String leaseToken, Instant expiresAt,
                                     Long contentRevision, String content, String reason) {
    }

    public record AiChapterEditResult(NovelChapter chapter, AiUndoView undo) {
    }

    public record AiUndoView(Long operationId, Long runId, Long chapterId, String chapterTitle, String status,
                             Long sequenceNo, Instant createdAt, Instant expiresAt, Instant undoneAt) {
    }

    public record AiUndoDismissResult(List<Long> operationIds) {
    }

    public record NovelDirectoryView(Long structureRevision, List<NovelVolume> volumes,
                                     List<NovelChapter> chapters) {
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
        requireVolume(projectId, command.volumeId());
        NovelChapter chapter = new NovelChapter();
        chapter.setChapterId(businessIdGenerator.nextId());
        chapter.setProjectId(projectId);
        chapter.setVolumeId(command.volumeId());
        chapter.setTitle(command.title());
        chapter.setSortOrder(command.sortOrder());
        chapter.setWordCount(0);
        chapter.setContent("");
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
        rejectActiveAiLease(chapter);
        chapter.setTitle(command.title());
        int affected = novelGateway.updateChapter(chapter);
        if (affected != 1) {
            NovelChapter current = novelGateway.findChapterByIdAndProjectId(projectId, chapterId);
            rejectActiveAiLease(current);
            log.error("更新章节失败: projectId={}, chapterId={}", projectId, chapterId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to update chapter");
        }

        log.info("更新章节成功: projectId={}, chapterId={}", projectId, chapterId);
        return getChapter(projectId, chapterId);
    }

    @Transactional
    public NovelDirectoryView moveDirectoryItem(Long projectId, MoveDirectoryItemCommand command,
                                                Long operatorId, String traceId) {
        NovelProject project = lockOwnedProject(projectId, operatorId);
        if (!Objects.equals(project.getStructureRevision(), command.expectedStructureRevision())) {
            throw com.penmate.backend.application.common.exception.BusinessException.conflict(
                    "The directory changed elsewhere. Refresh and try again");
        }

        boolean changed = command.nodeType() == DirectoryNodeType.VOLUME
                ? moveVolume(projectId, command.nodeId(), command.sortOrder())
                : moveChapter(projectId, command.nodeId(), command.targetVolumeId(), command.sortOrder());
        if (!changed) {
            return directoryView(projectId, project.getStructureRevision());
        }

        incrementStructureRevision(projectId);
        return directoryView(projectId, project.getStructureRevision() + 1);
    }

    private boolean moveVolume(Long projectId, Long volumeId, Integer sortOrder) {
        List<NovelVolume> volumes = new ArrayList<>(novelGateway.findVolumesByProjectId(projectId));
        int currentIndex = -1;
        for (int index = 0; index < volumes.size(); index++) {
            if (Objects.equals(volumes.get(index).getVolumeId(), volumeId)) {
                currentIndex = index;
                break;
            }
        }
        if (currentIndex < 0) {
            throw com.penmate.backend.application.common.exception.BusinessException.notFound("Volume not found");
        }
        int targetIndex = Math.max(0, Math.min(volumes.size() - 1, sortOrder - 1));
        if (currentIndex == targetIndex) return false;

        NovelVolume moved = volumes.remove(currentIndex);
        volumes.add(targetIndex, moved);
        for (int index = 0; index < volumes.size(); index++) {
            NovelVolume volume = volumes.get(index);
            volume.setSortOrder(index + 1);
            if (novelGateway.updateVolume(volume) != 1) {
                throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to reorder volumes");
            }
        }
        return true;
    }

    private boolean moveChapter(Long projectId, Long chapterId, Long targetVolumeId, Integer sortOrder) {
        if (targetVolumeId == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.badRequest(
                    "Target volume is required for a chapter move");
        }
        List<NovelVolume> volumes = novelGateway.findVolumesByProjectId(projectId);
        if (volumes.stream().noneMatch(volume -> Objects.equals(volume.getVolumeId(), targetVolumeId))) {
            throw com.penmate.backend.application.common.exception.BusinessException.notFound("Target volume not found");
        }

        List<NovelChapter> allChapters = novelGateway.findChaptersByProjectId(projectId);
        NovelChapter chapter = allChapters.stream()
                .filter(item -> Objects.equals(item.getChapterId(), chapterId))
                .findFirst()
                .orElseThrow(() -> com.penmate.backend.application.common.exception.BusinessException.notFound(
                        "Chapter not found"));
        Long sourceVolumeId = chapter.getVolumeId();
        List<NovelChapter> source = new ArrayList<>(allChapters.stream()
                .filter(item -> Objects.equals(item.getVolumeId(), sourceVolumeId))
                .toList());
        int currentIndex = source.indexOf(chapter);
        int destinationSize = (int) allChapters.stream()
                .filter(item -> Objects.equals(item.getVolumeId(), targetVolumeId))
                .filter(item -> !Objects.equals(item.getChapterId(), chapterId))
                .count();
        int targetIndex = Math.max(0, Math.min(destinationSize, sortOrder - 1));
        if (Objects.equals(sourceVolumeId, targetVolumeId) && currentIndex == targetIndex) return false;

        List<NovelChapter> destination = new ArrayList<>(allChapters.stream()
                .filter(item -> Objects.equals(item.getVolumeId(), targetVolumeId))
                .filter(item -> !Objects.equals(item.getChapterId(), chapterId))
                .toList());
        chapter.setVolumeId(targetVolumeId);
        destination.add(targetIndex, chapter);
        resequenceChapters(destination);
        if (!Objects.equals(sourceVolumeId, targetVolumeId)) {
            resequenceChapters(source.stream()
                    .filter(item -> !Objects.equals(item.getChapterId(), chapterId))
                    .toList());
        }
        return true;
    }

    private void resequenceChapters(List<NovelChapter> chapters) {
        for (int index = 0; index < chapters.size(); index++) {
            NovelChapter chapter = chapters.get(index);
            chapter.setSortOrder(index + 1);
            if (novelGateway.updateChapter(chapter) != 1) {
                throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to reorder chapters");
            }
        }
    }

    private NovelVolume requireVolume(Long projectId, Long volumeId) {
        if (volumeId == null) {
            throw com.penmate.backend.application.common.exception.BusinessException.badRequest("Volume is required");
        }
        return novelGateway.findVolumesByProjectId(projectId).stream()
                .filter(volume -> Objects.equals(volume.getVolumeId(), volumeId))
                .findFirst()
                .orElseThrow(() -> com.penmate.backend.application.common.exception.BusinessException.of("Volume not found"));
    }

    private NovelProject lockOwnedProject(Long projectId, Long actorUserId) {
        NovelProject project = novelGateway.lockProject(projectId);
        if (project == null || !Objects.equals(project.getOwnerUserId(), actorUserId)) {
            throw com.penmate.backend.application.common.exception.BusinessException.notFound("Project not found");
        }
        return project;
    }

    private NovelDirectoryView directoryView(Long projectId, Long structureRevision) {
        return new NovelDirectoryView(structureRevision, listVolumes(projectId), listChapters(projectId));
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
        rejectActiveAiLease(novelGateway.findChapterByIdAndProjectId(projectId, chapterId));
        int affected = novelGateway.softDeleteChapter(projectId, chapterId);
        if (affected != 1) {
            rejectActiveAiLease(novelGateway.findChapterByIdAndProjectId(projectId, chapterId));
            log.warn("删除章节失败: projectId={}, chapterId={}, reason=not_found_or_deleted", projectId, chapterId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Chapter not found or already deleted");
        }
        incrementStructureRevision(projectId);
         
        log.info("删除章节成功: projectId={}, chapterId={}", projectId, chapterId);
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
        return value(chapter.getContent());
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


