package com.penmate.backend.interfaces.api.novel;

import com.penmate.backend.application.novel.NovelApplicationService;
import com.penmate.backend.application.novel.command.NovelCommands;
import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.domain.novel.model.NovelVolume;
import com.penmate.backend.domain.novel.model.NovelOutlineNode;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import com.penmate.backend.interfaces.api.novel.dto.CreateNovelChapterDto;
import com.penmate.backend.interfaces.api.novel.dto.CreateChapterVersionDto;
import com.penmate.backend.interfaces.api.novel.dto.CreateNovelOutlineNodeDto;
import com.penmate.backend.interfaces.api.novel.dto.CreateNovelProjectDto;
import com.penmate.backend.interfaces.api.novel.dto.CreateNovelVolumeDto;
import com.penmate.backend.interfaces.api.novel.dto.AddNovelMemberDto;
import com.penmate.backend.interfaces.api.novel.dto.CommitChapterContentDto;
import com.penmate.backend.interfaces.api.novel.dto.MoveNovelOutlineNodeDto;
import com.penmate.backend.interfaces.api.novel.dto.UpdateNovelMemberDto;
import com.penmate.backend.interfaces.api.novel.dto.UpdateNovelChapterDto;
import com.penmate.backend.interfaces.api.novel.dto.MoveNovelChapterDto;
import com.penmate.backend.interfaces.api.novel.dto.UpdateNovelOutlineNodeDto;
import com.penmate.backend.interfaces.api.novel.dto.UpdateNovelProjectDto;
import com.penmate.backend.interfaces.api.novel.dto.UpdateNovelVolumeDto;
import com.penmate.backend.domain.novel.model.NovelChapterVersion;
import com.penmate.backend.domain.novel.model.NovelMember;
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

import java.util.List;
import java.util.Map;

/**
 * 小说项目聚合控制器。
 * <p>统一提供项目、卷、章节、成员、版本、大纲、卡片与关系等写作域接口，并将 HTTP 参数映射为应用层命令对象。</p>
 */
@RestController
@RequestMapping("/api/v1/novels")
public class NovelController {

    private final NovelApplicationService novelApplicationService;

    public NovelController(NovelApplicationService novelApplicationService) {
        this.novelApplicationService = novelApplicationService;
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

    /**
     * 查询小说项目列表。
     * <p>流程：调用应用服务读取项目清单并返回统一响应。</p>
     *
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping
    public ApiResponse<List<NovelProject>> listProjects(@RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.listProjects(), traceId);
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
                                                   @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.createProject(
                new NovelCommands.CreateProjectCommand(
                        requireLongId(dto.getOwnerUserId(), "ownerUserId"),
                        dto.getTitle(),
                        dto.getSummary(),
                        dto.getStatus()
                ),
                traceId
        ), traceId);
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
        return ApiResponse.success(novelApplicationService.getProject(requireLongId(projectId, "projectId")), traceId);
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
        return ApiResponse.success(novelApplicationService.updateProject(
                requireLongId(projectId, "projectId"),
                new NovelCommands.UpdateProjectCommand(dto.getTitle(), dto.getSummary(), dto.getStatus()),
                traceId
        ), traceId);
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
                                             @RequestParam("operatorId") String operatorId,
                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        novelApplicationService.deleteProject(requireLongId(projectId, "projectId"), requireLongId(operatorId, "operatorId"), traceId);
        return ApiResponse.success("deleted", traceId);
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
                                                   @RequestParam("operatorId") String operatorId,
                                                   @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.createVolume(
                requireLongId(projectId, "projectId"),
                new NovelCommands.CreateVolumeCommand(dto.getTitle(), dto.getSortOrder(), dto.getDescription()),
                requireLongId(operatorId, "operatorId"),
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
                                                   @RequestParam("operatorId") String operatorId,
                                                   @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.updateVolume(
                requireLongId(projectId, "projectId"),
                requireLongId(volumeId, "volumeId"),
                new NovelCommands.UpdateVolumeCommand(dto.getTitle(), dto.getSortOrder(), dto.getDescription()),
                requireLongId(operatorId, "operatorId"),
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
                                            @RequestParam("operatorId") String operatorId,
                                            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        novelApplicationService.deleteVolume(requireLongId(projectId, "projectId"), requireLongId(volumeId, "volumeId"), requireLongId(operatorId, "operatorId"), traceId);
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
                                                     @RequestParam("operatorId") String operatorId,
                                                     @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.createChapter(
                requireLongId(projectId, "projectId"),
                new NovelCommands.CreateChapterCommand(
                        optionalLongId(dto.getVolumeId()),
                        optionalLongId(dto.getOutlineNodeId()),
                        dto.getTitle(),
                        dto.getSortOrder(),
                        dto.getStatus(),
                        dto.getWordCount(),
                        dto.getExcerpt(),
                        dto.getContentObjectKey(),
                        dto.getContentEtag(),
                        dto.getContentSize(),
                        dto.getContentChecksum(),
                        dto.getStorageProvider()
                ),
                requireLongId(operatorId, "operatorId"),
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
                                                     @RequestParam("operatorId") String operatorId,
                                                     @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.updateChapter(
                requireLongId(projectId, "projectId"),
                requireLongId(chapterId, "chapterId"),
                new NovelCommands.UpdateChapterCommand(
                        optionalLongId(dto.getVolumeId()),
                        optionalLongId(dto.getOutlineNodeId()),
                        dto.getTitle(),
                        dto.getSortOrder(),
                        dto.getStatus(),
                        dto.getWordCount(),
                        dto.getExcerpt(),
                        dto.getContentObjectKey(),
                        dto.getContentEtag(),
                        dto.getContentSize(),
                        dto.getContentChecksum(),
                        dto.getStorageProvider()
                ),
                requireLongId(operatorId, "operatorId"),
                traceId
        ), traceId);
    }

    @PatchMapping("/{projectId}/chapters/{chapterId}/position")
    public ApiResponse<NovelChapter> moveChapter(@PathVariable String projectId,
                                                 @PathVariable String chapterId,
                                                 @Valid @RequestBody MoveNovelChapterDto dto,
                                                 @RequestParam("operatorId") String operatorId,
                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.moveChapter(
                requireLongId(projectId, "projectId"),
                requireLongId(chapterId, "chapterId"),
                new NovelCommands.MoveChapterCommand(optionalLongId(dto.getVolumeId()), dto.getSortOrder()),
                requireLongId(operatorId, "operatorId"),
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
                                             @RequestParam("operatorId") String operatorId,
                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        novelApplicationService.deleteChapter(requireLongId(projectId, "projectId"), requireLongId(chapterId, "chapterId"), requireLongId(operatorId, "operatorId"), traceId);
        return ApiResponse.success("deleted", traceId);
    }

    /**
     * 发布章节。
     * <p>流程：触发章节发布状态流转并返回 published。</p>
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/{projectId}/chapters/{chapterId}/publish")
    public ApiResponse<String> publishChapter(@PathVariable String projectId,
                                              @PathVariable String chapterId,
                                              @RequestParam("operatorId") String operatorId,
                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        novelApplicationService.publishChapter(requireLongId(projectId, "projectId"), requireLongId(chapterId, "chapterId"), requireLongId(operatorId, "operatorId"), traceId);
        return ApiResponse.success("published", traceId);
    }

    /**
     * 查询项目成员列表。
     * <p>流程：读取项目成员关系并返回。</p>
     *
     * @param projectId 入参：projectId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/{projectId}/members")
    public ApiResponse<List<NovelMember>> listMembers(@PathVariable String projectId,
                                                      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.listMembers(requireLongId(projectId, "projectId")), traceId);
    }

    /**
     * 新增项目成员。
     * <p>流程：组装成员命令 -> 应用服务写入成员关系。</p>
     *
     * @param projectId 入参：projectId
     * @param dto 入参：dto
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/{projectId}/members")
    public ApiResponse<NovelMember> addMember(@PathVariable String projectId,
                                               @Valid @RequestBody AddNovelMemberDto dto,
                                               @RequestParam("operatorId") String operatorId,
                                               @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.addMember(
                requireLongId(projectId, "projectId"),
                new NovelCommands.AddMemberCommand(requireLongId(dto.getUserId(), "userId"), dto.getMemberRole()),
                requireLongId(operatorId, "operatorId"),
                traceId
        ), traceId);
    }

    /**
     * 更新成员角色。
     * <p>流程：按用户ID更新成员角色与权限范围。</p>
     *
     * @param projectId 入参：projectId
     * @param userId 入参：userId
     * @param dto 入参：dto
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @org.springframework.web.bind.annotation.PatchMapping("/{projectId}/members/{userId}")
    public ApiResponse<NovelMember> updateMember(@PathVariable String projectId,
                                                  @PathVariable String userId,
                                                   @Valid @RequestBody UpdateNovelMemberDto dto,
                                                   @RequestParam("operatorId") String operatorId,
                                                   @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.updateMember(
                requireLongId(projectId, "projectId"),
                requireLongId(userId, "userId"),
                new NovelCommands.UpdateMemberCommand(dto.getMemberRole()),
                requireLongId(operatorId, "operatorId"),
                traceId
        ), traceId);
    }

    /**
     * 移除项目成员。
     * <p>流程：校验操作者权限 -> 解除成员关系。</p>
     *
     * @param projectId 入参：projectId
     * @param userId 入参：userId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @DeleteMapping("/{projectId}/members/{userId}")
    public ApiResponse<String> removeMember(@PathVariable String projectId,
                                            @PathVariable String userId,
                                            @RequestParam("operatorId") String operatorId,
                                            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        novelApplicationService.removeMember(requireLongId(projectId, "projectId"), requireLongId(userId, "userId"), requireLongId(operatorId, "operatorId"), traceId);
        return ApiResponse.success("removed", traceId);
    }

    /**
     * 查询章节版本列表。
     * <p>流程：读取章节历史版本并返回。</p>
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/{projectId}/chapters/{chapterId}/versions")
    public ApiResponse<List<NovelChapterVersion>> listChapterVersions(@PathVariable String projectId,
                                                                      @PathVariable String chapterId,
                                                                      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.listChapterVersions(requireLongId(projectId, "projectId"), requireLongId(chapterId, "chapterId")), traceId);
    }

    /**
     * 创建章节版本快照。
     * <p>流程：根据变更原因创建新版本记录。</p>
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param dto 入参：dto
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/{projectId}/chapters/{chapterId}/versions")
    public ApiResponse<NovelChapterVersion> createChapterVersion(@PathVariable String projectId,
                                                                  @PathVariable String chapterId,
                                                                  @Valid @RequestBody CreateChapterVersionDto dto,
                                                                  @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.createChapterVersion(
                requireLongId(projectId, "projectId"),
                requireLongId(chapterId, "chapterId"),
                new NovelCommands.CreateChapterVersionCommand(dto.getChangeType(), dto.getChangeReason(), requireLongId(dto.getCreatedBy(), "createdBy")),
                traceId
        ), traceId);
    }

    /**
     * 查询单个章节版本。
     * <p>流程：按 versionNo 查询指定版本快照。</p>
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param versionNo 入参：versionNo
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/{projectId}/chapters/{chapterId}/versions/{versionNo}")
    public ApiResponse<NovelChapterVersion> getChapterVersion(@PathVariable String projectId,
                                                              @PathVariable String chapterId,
                                                              @PathVariable Integer versionNo,
                                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.getChapterVersion(requireLongId(projectId, "projectId"), requireLongId(chapterId, "chapterId"), versionNo), traceId);
    }

    /**
     * 恢复到历史章节版本。
     * <p>流程：校验版本存在 -> 执行版本回滚并返回章节。</p>
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param versionNo 入参：versionNo
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/{projectId}/chapters/{chapterId}/versions/{versionNo}/restore")
    public ApiResponse<NovelChapter> restoreChapterVersion(@PathVariable String projectId,
                                                           @PathVariable String chapterId,
                                                           @PathVariable Integer versionNo,
                                                           @RequestParam("operatorId") String operatorId,
                                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.restoreChapterVersion(requireLongId(projectId, "projectId"), requireLongId(chapterId, "chapterId"), versionNo, requireLongId(operatorId, "operatorId"), traceId), traceId);
    }

    /**
     * 获取章节正文下载地址。
     * <p>流程：生成或读取正文对象访问地址。</p>
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/{projectId}/chapters/{chapterId}/content-url")
    public ApiResponse<Map<String, String>> getChapterContentUrl(@PathVariable String projectId,
                                                                 @PathVariable String chapterId,
                                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.getChapterContentUrl(requireLongId(projectId, "projectId"), requireLongId(chapterId, "chapterId")), traceId);
    }

    /**
     * 获取章节正文上传地址。
     * <p>流程：申请上传URL用于前端直传正文文件。</p>
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/{projectId}/chapters/{chapterId}/content-upload-url")
    public ApiResponse<Map<String, String>> getChapterContentUploadUrl(@PathVariable String projectId,
                                                                       @PathVariable String chapterId,
                                                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.getChapterContentUploadUrl(requireLongId(projectId, "projectId"), requireLongId(chapterId, "chapterId")), traceId);
    }

    /**
     * 提交章节正文对象变更。
     * <p>流程：提交对象存储元信息 -> 应用服务更新章节正文引用。</p>
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param dto 入参：dto
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/{projectId}/chapters/{chapterId}/content-commit")
    public ApiResponse<NovelChapter> commitChapterContent(@PathVariable String projectId,
                                                           @PathVariable String chapterId,
                                                            @Valid @RequestBody CommitChapterContentDto dto,
                                                            @RequestParam("operatorId") String operatorId,
                                                            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.commitChapterContent(
                requireLongId(projectId, "projectId"),
                requireLongId(chapterId, "chapterId"),
                new NovelCommands.CommitChapterContentCommand(
                        dto.getObjectKey(),
                        dto.getEtag(),
                        dto.getSize(),
                        dto.getChecksum(),
                        dto.getStorageProvider(),
                        dto.getContent()
                ),
                requireLongId(operatorId, "operatorId"),
                traceId
        ), traceId);
    }

    /**
     * 获取章节版本快照下载地址。
     * <p>流程：按版本号返回快照对象访问地址。</p>
     *
     * @param projectId 入参：projectId
     * @param chapterId 入参：chapterId
     * @param versionNo 入参：versionNo
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/{projectId}/chapters/{chapterId}/versions/{versionNo}/snapshot-url")
    public ApiResponse<Map<String, String>> getChapterVersionSnapshotUrl(@PathVariable String projectId,
                                                                          @PathVariable String chapterId,
                                                                          @PathVariable Integer versionNo,
                                                                          @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.getChapterVersionSnapshotUrl(requireLongId(projectId, "projectId"), requireLongId(chapterId, "chapterId"), versionNo), traceId);
    }

    /**
     * 查询大纲树。
     * <p>流程：按项目读取树形大纲节点。</p>
     *
     * @param projectId 入参：projectId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/{projectId}/outlines/tree")
    public ApiResponse<List<NovelOutlineNode>> listOutlineTree(@PathVariable String projectId,
                                                               @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.listOutlineTree(requireLongId(projectId, "projectId")), traceId);
    }

    /**
     * 创建大纲节点。
     * <p>流程：组装节点创建命令 -> 写入并返回节点。</p>
     *
     * @param projectId 入参：projectId
     * @param dto 入参：dto
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/{projectId}/outlines/nodes")
    public ApiResponse<NovelOutlineNode> createOutlineNode(@PathVariable String projectId,
                                                             @Valid @RequestBody CreateNovelOutlineNodeDto dto,
                                                             @RequestParam("operatorId") String operatorId,
                                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.createOutlineNode(
                requireLongId(projectId, "projectId"),
                new NovelCommands.CreateOutlineNodeCommand(
                        optionalLongId(dto.getParentId()),
                        dto.getTitle(),
                        dto.getNodeType(),
                        dto.getSortOrder(),
                        dto.getContent()
                ),
                requireLongId(operatorId, "operatorId"),
                traceId
        ), traceId);
    }

    /**
     * 更新大纲节点。
     * <p>流程：按节点ID更新标题/类型/排序与内容。</p>
     *
     * @param projectId 入参：projectId
     * @param nodeId 入参：nodeId
     * @param dto 入参：dto
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PutMapping("/{projectId}/outlines/nodes/{nodeId}")
    public ApiResponse<NovelOutlineNode> updateOutlineNode(@PathVariable String projectId,
                                                            @PathVariable String nodeId,
                                                             @Valid @RequestBody UpdateNovelOutlineNodeDto dto,
                                                             @RequestParam("operatorId") String operatorId,
                                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(novelApplicationService.updateOutlineNode(
                requireLongId(projectId, "projectId"),
                requireLongId(nodeId, "nodeId"),
                new NovelCommands.UpdateOutlineNodeCommand(
                        optionalLongId(dto.getParentId()),
                        dto.getTitle(),
                        dto.getNodeType(),
                        dto.getSortOrder(),
                        dto.getContent()
                ),
                requireLongId(operatorId, "operatorId"),
                traceId
        ), traceId);
    }

    /**
     * 移动大纲节点。
     * <p>流程：更新父节点与排序号，完成树结构重排。</p>
     *
     * @param projectId 入参：projectId
     * @param nodeId 入参：nodeId
     * @param dto 入参：dto
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @org.springframework.web.bind.annotation.PatchMapping("/{projectId}/outlines/nodes/{nodeId}/move")
    public ApiResponse<String> moveOutlineNode(@PathVariable String projectId,
                                                @PathVariable String nodeId,
                                                 @Valid @RequestBody MoveNovelOutlineNodeDto dto,
                                                 @RequestParam("operatorId") String operatorId,
                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        novelApplicationService.moveOutlineNode(
                requireLongId(projectId, "projectId"),
                requireLongId(nodeId, "nodeId"),
                new NovelCommands.MoveOutlineNodeCommand(optionalLongId(dto.getParentId()), dto.getSortOrder()),
                requireLongId(operatorId, "operatorId"),
                traceId
        );
        return ApiResponse.success("moved", traceId);
    }

    /**
     * 删除大纲节点。
     * <p>流程：校验节点可删后执行删除。</p>
     *
     * @param projectId 入参：projectId
     * @param nodeId 入参：nodeId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @DeleteMapping("/{projectId}/outlines/nodes/{nodeId}")
    public ApiResponse<String> deleteOutlineNode(@PathVariable String projectId,
                                                 @PathVariable String nodeId,
                                                 @RequestParam("operatorId") String operatorId,
                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        novelApplicationService.deleteOutlineNode(requireLongId(projectId, "projectId"), requireLongId(nodeId, "nodeId"), requireLongId(operatorId, "operatorId"), traceId);
        return ApiResponse.success("deleted", traceId);
    }

    /**
     * 查询卡片列表。
     * <p>流程：读取项目卡片并返回。</p>
     *
     * @param projectId 入参：projectId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */

    /**
     * 创建卡片。
     * <p>流程：按卡片类型与内容创建角色/世界观等卡片。</p>
     *
     * @param projectId 入参：projectId
     * @param dto 入参：dto
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */

    /**
     * 查询卡片详情。
     * <p>流程：按卡片ID查询并返回。</p>
     *
     * @param projectId 入参：projectId
     * @param cardId 入参：cardId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */

    /**
     * 更新卡片。
     * <p>流程：提交更新命令并保存卡片内容。</p>
     *
     * @param projectId 入参：projectId
     * @param cardId 入参：cardId
     * @param dto 入参：dto
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */

    /**
     * 删除卡片。
     * <p>流程：按卡片ID删除并返回确认结果。</p>
     *
     * @param projectId 入参：projectId
     * @param cardId 入参：cardId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */

    /**
     * 查询卡片关系列表。
     * <p>流程：读取项目内卡片关系边集合。</p>
     *
     * @param projectId 入参：projectId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */

    /**
     * 创建卡片关系。
     * <p>流程：提交关联关系命令并持久化。</p>
     *
     * @param projectId 入参：projectId
     * @param dto 入参：dto
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */

    /**
     * 删除卡片关系。
     * <p>流程：按关系ID删除关联边。</p>
     *
     * @param projectId 入参：projectId
     * @param relationId 入参：relationId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
}

