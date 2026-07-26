package com.penmate.backend.interfaces.api.novel;

import com.penmate.backend.application.novel.NovelApplicationService;
import com.penmate.backend.application.novel.NovelCoverApplicationService;
import com.penmate.backend.application.novel.NovelTrashApplicationService;
import com.penmate.backend.application.novel.command.NovelCommands;
import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.model.NovelVolume;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import com.penmate.backend.interfaces.api.novel.dto.CreateNovelChapterDto;
import com.penmate.backend.interfaces.api.novel.dto.CreateNovelProjectDto;
import com.penmate.backend.interfaces.api.novel.dto.CreateNovelVolumeDto;
import com.penmate.backend.interfaces.api.novel.dto.UpdateNovelChapterDto;
import com.penmate.backend.interfaces.api.novel.dto.MoveNovelDirectoryItemDto;
import com.penmate.backend.interfaces.api.novel.dto.PermanentlyDeleteNovelProjectDto;
import com.penmate.backend.interfaces.api.novel.dto.UpdateNovelProjectDto;
import com.penmate.backend.interfaces.api.novel.dto.UpdateNovelVolumeDto;
import com.penmate.backend.interfaces.api.novel.dto.SaveChapterContentDto;
import com.penmate.backend.interfaces.api.novel.dto.DismissAiUndoDto;
import com.penmate.backend.interfaces.api.novel.dto.CompleteNovelCoverUploadDto;
import com.penmate.backend.interfaces.api.novel.dto.InitializeNovelCoverUploadDto;
import com.penmate.backend.interfaces.api.novel.dto.NovelCoverCropDto;
import com.penmate.backend.interfaces.api.novel.dto.RecropNovelCoverDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import static com.penmate.backend.interfaces.api.common.AuthenticatedActor.id;

import java.util.List;

/**
 * 小说项目聚合控制器。
 * <p>统一提供项目、卷、章节、成员、版本、大纲、卡片与关系等写作域接口，并将 HTTP 参数映射为应用层命令对象。</p>
 */
@RestController
@RequestMapping("/api/v1/novels")
public class NovelController {

    private final NovelApplicationService novelApplicationService;
    private final NovelTrashApplicationService novelTrashApplicationService;
    private final NovelCoverApplicationService novelCoverApplicationService;

    public NovelController(NovelApplicationService novelApplicationService,
                           NovelTrashApplicationService novelTrashApplicationService,
                           NovelCoverApplicationService novelCoverApplicationService) {
        this.novelApplicationService = novelApplicationService;
        this.novelTrashApplicationService = novelTrashApplicationService;
        this.novelCoverApplicationService = novelCoverApplicationService;
    }

    private Long requireLongId(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return Long.valueOf(raw.trim());
    }

    private Long optionalLongId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Long.valueOf(raw.trim());
    }

    private NovelCoverApplicationService.CropRequest crop(NovelCoverCropDto dto) {
        return new NovelCoverApplicationService.CropRequest(dto.getX(), dto.getY(), dto.getWidth(), dto.getHeight());
    }

    /**
     * 查询小说项目列表。
     * <p>流程：调用应用服务读取项目清单并返回统一响应。</p>
     *
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping
    public ApiResponse<List<NovelProject>> listProjects(Authentication authentication,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.listProjects(id(authentication)).stream()
                .map(novelCoverApplicationService::decorate).toList(), traceId);
    }

    @GetMapping("/trash")
    public ApiResponse<List<NovelProject>> listDeletedProjects(
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelTrashApplicationService.listDeletedProjects(id(authentication)).stream()
                .map(novelCoverApplicationService::decorate).toList(), traceId);
    }

    @PostMapping("/trash/{projectId}/restore")
    public ApiResponse<NovelProject> restoreDeletedProject(
            @PathVariable String projectId,
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelCoverApplicationService.decorate(novelTrashApplicationService.restoreProject(
                requireLongId(projectId, "projectId"), id(authentication))), traceId);
    }

    @DeleteMapping("/trash/{projectId}")
    public ApiResponse<String> permanentlyDeleteProject(
            @PathVariable String projectId,
            @Valid @RequestBody PermanentlyDeleteNovelProjectDto dto,
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        novelTrashApplicationService.permanentlyDeleteProject(
                requireLongId(projectId, "projectId"), id(authentication), dto.getConfirmationTitle());
        return ApiResponse.success("deleted", traceId);
    }

    /**
     * 创建小说项目。
     * <p>流程：校验入参 -> 组装创建命令 -> 调用应用服务落库。</p>
     *
     * @param dto 入参：dto
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping
    public ApiResponse<NovelProject> createProject(@Valid @RequestBody CreateNovelProjectDto dto,
                                                   Authentication authentication,
                                                   @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelCoverApplicationService.decorate(novelApplicationService.createProject(
                new NovelCommands.CreateProjectCommand(
                        id(authentication),
                        dto.getTitle(),
                        dto.getSummary(),
                        dto.getGenre(),
                        dto.getCustomGenre(),
                        dto.getTags(),
                        dto.getStatus()
                ),
                traceId
        )), traceId);
    }

    /**
     * 查询项目详情。
     * <p>流程：按项目业务ID查询项目实体并返回。</p>
     *
     * @param projectId 入参：projectId（项目业务ID）
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/{projectId}")
    public ApiResponse<NovelProject> getProject(@PathVariable String projectId,
                                                @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelCoverApplicationService.decorate(
                novelApplicationService.getProject(requireLongId(projectId, "projectId"))), traceId);
    }

    /**
     * 更新项目基础信息。
     * <p>流程：组装更新命令 -> 应用服务按项目业务ID校验并保存。</p>
     *
     * @param projectId 入参：projectId（项目业务ID）
     * @param dto 入参：dto
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PutMapping("/{projectId}")
    public ApiResponse<NovelProject> updateProject(@PathVariable String projectId,
                                                   @Valid @RequestBody UpdateNovelProjectDto dto,
                                                   @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelCoverApplicationService.decorate(novelApplicationService.updateProject(
                requireLongId(projectId, "projectId"),
                new NovelCommands.UpdateProjectCommand(dto.getTitle(), dto.getSummary(), dto.getGenre(),
                        dto.getCustomGenre(), dto.getTags(), dto.getStatus()),
                traceId
        )), traceId);
    }

    /**
     * 删除项目。
     * <p>流程：校验操作者权限 -> 按项目业务ID执行项目删除。</p>
     *
     * @param projectId 入参：projectId（项目业务ID）
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @DeleteMapping("/{projectId}")
    public ApiResponse<String> deleteProject(@PathVariable String projectId,
                                             Authentication authentication,
                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        novelApplicationService.deleteProject(requireLongId(projectId, "projectId"), id(authentication), traceId);
        return ApiResponse.success("deleted", traceId);
    }

    @PostMapping("/{projectId}/cover/uploads")
    public ApiResponse<NovelCoverApplicationService.UploadInitialization> initializeCoverUpload(
            @PathVariable String projectId,
            @Valid @RequestBody InitializeNovelCoverUploadDto dto,
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelCoverApplicationService.initializeUpload(
                requireLongId(projectId, "projectId"), id(authentication),
                new NovelCoverApplicationService.UploadRequest(
                        dto.getFilename(), dto.getMimeType(), dto.getSize(), dto.getSha256())), traceId);
    }

    @PostMapping("/{projectId}/cover/uploads/{uploadId}/complete")
    public ApiResponse<NovelCoverApplicationService.CoverState> completeCoverUpload(
            @PathVariable String projectId, @PathVariable String uploadId,
            @Valid @RequestBody CompleteNovelCoverUploadDto dto,
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelCoverApplicationService.completeUpload(
                requireLongId(projectId, "projectId"), id(authentication), requireLongId(uploadId, "uploadId"),
                dto.getUploadToken(), crop(dto.getCrop())), traceId);
    }

    @GetMapping("/{projectId}/cover")
    public ApiResponse<NovelCoverApplicationService.CoverState> getCover(
            @PathVariable String projectId, Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelCoverApplicationService.getState(
                requireLongId(projectId, "projectId"), id(authentication)), traceId);
    }

    @PostMapping("/{projectId}/cover/crops")
    public ApiResponse<NovelCoverApplicationService.CoverState> recropCover(
            @PathVariable String projectId, @Valid @RequestBody RecropNovelCoverDto dto,
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelCoverApplicationService.recrop(
                requireLongId(projectId, "projectId"), id(authentication), crop(dto.getCrop())), traceId);
    }

    @PostMapping("/{projectId}/cover/uploads/{uploadId}/retry")
    public ApiResponse<NovelCoverApplicationService.CoverState> retryCover(
            @PathVariable String projectId, @PathVariable String uploadId,
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelCoverApplicationService.retry(
                requireLongId(projectId, "projectId"), id(authentication), requireLongId(uploadId, "uploadId")), traceId);
    }

    @DeleteMapping("/{projectId}/cover")
    public ApiResponse<String> removeCover(
            @PathVariable String projectId, Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        novelCoverApplicationService.remove(requireLongId(projectId, "projectId"), id(authentication));
        return ApiResponse.success("removed", traceId);
    }

    /**
     * 查询项目卷列表。
     * <p>流程：按项目业务ID读取卷集合。</p>
     *
     * @param projectId 入参：projectId（项目业务ID）
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/{projectId}/volumes")
    public ApiResponse<List<NovelVolume>> listVolumes(@PathVariable String projectId,
                                                      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.listVolumes(requireLongId(projectId, "projectId")), traceId);
    }

    /**
     * 创建卷。
     * <p>流程：组装卷创建命令 -> 以项目业务ID写入卷记录。</p>
     *
     * @param projectId 入参：projectId（项目业务ID）
     * @param dto 入参：dto
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/{projectId}/volumes")
    public ApiResponse<NovelVolume> createVolume(@PathVariable String projectId,
                                                   @Valid @RequestBody CreateNovelVolumeDto dto,
                                                   Authentication authentication,
                                                   @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.createVolume(
                requireLongId(projectId, "projectId"),
                new NovelCommands.CreateVolumeCommand(dto.getTitle(), dto.getSortOrder(), dto.getDescription()),
                id(authentication),
                traceId
        ), traceId);
    }

    /**
     * 更新卷信息。
     * <p>流程：按卷业务ID执行更新并返回新状态。</p>
     *
     * @param projectId 入参：projectId（项目业务ID）
     * @param volumeId 入参：volumeId（卷业务ID）
     * @param dto 入参：dto
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PutMapping("/{projectId}/volumes/{volumeId}")
    public ApiResponse<NovelVolume> updateVolume(@PathVariable String projectId,
                                                  @PathVariable String volumeId,
                                                   @Valid @RequestBody UpdateNovelVolumeDto dto,
                                                   Authentication authentication,
                                                   @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.updateVolume(
                requireLongId(projectId, "projectId"),
                requireLongId(volumeId, "volumeId"),
                new NovelCommands.UpdateVolumeCommand(dto.getTitle(), dto.getSortOrder(), dto.getDescription()),
                id(authentication),
                traceId
        ), traceId);
    }

    /**
     * 删除卷。
     * <p>流程：校验卷业务ID归属与权限后执行删除。</p>
     *
     * @param projectId 入参：projectId（项目业务ID）
     * @param volumeId 入参：volumeId（卷业务ID）
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @DeleteMapping("/{projectId}/volumes/{volumeId}")
    public ApiResponse<String> deleteVolume(@PathVariable String projectId,
                                            @PathVariable String volumeId,
                                            Authentication authentication,
                                            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        novelApplicationService.deleteVolume(requireLongId(projectId, "projectId"), requireLongId(volumeId, "volumeId"), id(authentication), traceId);
        return ApiResponse.success("deleted", traceId);
    }

    /**
     * 查询章节列表。
     * <p>流程：按项目业务ID聚合章节并返回。</p>
     *
     * @param projectId 入参：projectId（项目业务ID）
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/{projectId}/chapters")
    public ApiResponse<List<NovelChapter>> listChapters(@PathVariable String projectId,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.listChapters(requireLongId(projectId, "projectId")), traceId);
    }

    /**
     * 创建章节。
     * <p>流程：组装章节创建命令 -> 持久化章节业务元数据。</p>
     *
     * @param projectId 入参：projectId（项目业务ID）
     * @param dto 入参：dto
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/{projectId}/chapters")
    public ApiResponse<NovelChapter> createChapter(@PathVariable String projectId,
                                                     @Valid @RequestBody CreateNovelChapterDto dto,
                                                     Authentication authentication,
                                                     @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.createChapter(
                requireLongId(projectId, "projectId"),
                new NovelCommands.CreateChapterCommand(
                        optionalLongId(dto.getVolumeId()),
                        dto.getTitle(),
                        dto.getSortOrder()
                ),
                id(authentication),
                traceId
        ), traceId);
    }

    /**
     * 查询章节详情。
     * <p>流程：按章节ID查询并返回章节对象。</p>
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/{projectId}/chapters/{chapterId}")
    public ApiResponse<NovelChapter> getChapter(@PathVariable String projectId,
                                                @PathVariable String chapterId,
                                                @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.getChapter(requireLongId(projectId, "projectId"), requireLongId(chapterId, "chapterId")), traceId);
    }

    @PutMapping("/{projectId}/chapters/{chapterId}/content")
    public ApiResponse<NovelChapter> saveChapterContent(
            @PathVariable String projectId, @PathVariable String chapterId,
            @Valid @RequestBody SaveChapterContentDto dto, Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.saveChapterContent(
                requireLongId(projectId, "projectId"), requireLongId(chapterId, "chapterId"),
                id(authentication), dto.getExpectedRevision(), dto.getContent()), traceId);
    }

    @GetMapping("/{projectId}/chapters/{chapterId}/ai-undo")
    public ApiResponse<List<NovelApplicationService.AiUndoView>> listChapterAiUndo(
            @PathVariable String projectId, @PathVariable String chapterId,
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.listAvailableAiUndoForChapter(
                requireLongId(projectId, "projectId"), requireLongId(chapterId, "chapterId"),
                id(authentication)), traceId);
    }

    @GetMapping("/{projectId}/ai-undo")
    public ApiResponse<List<NovelApplicationService.AiUndoView>> listProjectAiUndo(
            @PathVariable String projectId,
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.listAvailableAiUndoForProject(
                requireLongId(projectId, "projectId"), id(authentication)), traceId);
    }

    @PostMapping("/{projectId}/ai-edits/dismiss")
    public ApiResponse<NovelApplicationService.AiUndoDismissResult> dismissAiUndo(
            @PathVariable String projectId,
            @Valid @RequestBody DismissAiUndoDto dto,
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        List<Long> operationIds = dto.operationIds().stream()
                .map(operationId -> requireLongId(operationId, "operationId"))
                .toList();
        return ApiResponse.success(novelApplicationService.dismissAiUndo(
                requireLongId(projectId, "projectId"), operationIds, id(authentication)), traceId);
    }

    @PostMapping("/{projectId}/ai-edits/{operationId}/undo")
    public ApiResponse<NovelApplicationService.AiUndoView> undoChapterAiEdit(
            @PathVariable String projectId, @PathVariable String operationId,
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.undoAiChapterEdit(
                requireLongId(projectId, "projectId"), requireLongId(operationId, "operationId"),
                id(authentication)), traceId);
    }

    @PostMapping("/{projectId}/agent-runs/{runId}/chapter-edits/undo")
    public ApiResponse<List<NovelApplicationService.AiUndoView>> undoRunChapterAiEdits(
            @PathVariable String projectId, @PathVariable String runId,
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.undoAiChapterEditsForRun(
                requireLongId(projectId, "projectId"), requireLongId(runId, "runId"),
                id(authentication)), traceId);
    }

    /**
     * 更新章节元数据。
     * <p>流程：校验章节归属 -> 执行章节更新。</p>
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param dto 入参：dto
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PutMapping("/{projectId}/chapters/{chapterId}")
    public ApiResponse<NovelChapter> updateChapter(@PathVariable String projectId,
                                                    @PathVariable String chapterId,
                                                     @Valid @RequestBody UpdateNovelChapterDto dto,
                                                     Authentication authentication,
                                                     @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.updateChapter(
                requireLongId(projectId, "projectId"),
                requireLongId(chapterId, "chapterId"),
                new NovelCommands.UpdateChapterCommand(dto.getTitle()),
                id(authentication),
                traceId
        ), traceId);
    }

    @GetMapping("/{projectId}/directory")
    public ApiResponse<NovelApplicationService.NovelDirectoryView> getDirectory(
            @PathVariable String projectId,
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.getDirectory(
                requireLongId(projectId, "projectId"),
                id(authentication)
        ), traceId);
    }

    @PatchMapping("/{projectId}/directory/position")
    public ApiResponse<NovelApplicationService.NovelDirectoryView> moveDirectoryItem(
            @PathVariable String projectId,
            @Valid @RequestBody MoveNovelDirectoryItemDto dto,
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.moveDirectoryItem(
                requireLongId(projectId, "projectId"),
                new NovelCommands.MoveDirectoryItemCommand(
                        dto.getNodeType(),
                        requireLongId(dto.getNodeId(), "nodeId"),
                        optionalLongId(dto.getTargetVolumeId()),
                        dto.getSortOrder(),
                        dto.getExpectedStructureRevision()
                ),
                id(authentication),
                traceId
        ), traceId);
    }

    /**
     * 删除章节。
     * <p>流程：按章节ID执行删除并返回确认结果。</p>
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @DeleteMapping("/{projectId}/chapters/{chapterId}")
    public ApiResponse<String> deleteChapter(@PathVariable String projectId,
                                             @PathVariable String chapterId,
                                             Authentication authentication,
                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        novelApplicationService.deleteChapter(requireLongId(projectId, "projectId"), requireLongId(chapterId, "chapterId"), id(authentication), traceId);
        return ApiResponse.success("deleted", traceId);
    }

}
