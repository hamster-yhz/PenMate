package com.penmate.backend.interfaces.api.style;

import com.penmate.backend.application.style.StyleApplicationService;
import com.penmate.backend.application.style.command.StyleCommands;
import com.penmate.backend.domain.style.model.StyleProfile;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import com.penmate.backend.interfaces.api.style.dto.AnalyzeStyleSampleDto;
import com.penmate.backend.interfaces.api.style.dto.CreateStyleDto;
import com.penmate.backend.interfaces.api.style.dto.SwitchStyleDto;
import com.penmate.backend.interfaces.api.style.dto.UpdateStyleDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
 * 写作风格配置控制器。
 * <p>负责项目风格档案的增删改查、默认风格切换与样本文本风格分析。</p>
 */
@RestController
@RequestMapping("/api/v1/novels/{projectId}/styles")
public class StyleController {

    private final StyleApplicationService styleApplicationService;

    public StyleController(StyleApplicationService styleApplicationService) {
        this.styleApplicationService = styleApplicationService;
    }

    /**
     * 查询项目风格列表。
     * <p><b>业务目的：</b>返回项目下全部风格档案，供前端展示并选择当前写作风格。</p>
     * <p><b>流程主线：</b>读取项目业务 ID -> 调用应用服务查询风格列表 -> 封装统一响应。</p>
     * <p><b>关键调用：</b>{@code styleApplicationService.listStyles(projectId)}。</p>
     * <p><b>ID 语义：</b>projectId 为项目业务 ID。</p>
     * <p><b>异常与分支：</b>项目不存在时返回业务异常。</p>
     * <p><b>副作用：</b>无持久化写入。</p>
     */
    @GetMapping
    public ApiResponse<List<StyleProfile>> listStyles(@PathVariable Long projectId,
                                                      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(styleApplicationService.listStyles(projectId), traceId);
    }

    /**
     * 创建风格档案。
     * <p><b>业务目的：</b>新增一套可复用写作风格配置（节奏、语气、叙事焦点、提示模板等）。</p>
     * <p><b>流程主线：</b>校验请求体 -> 组装 {@link StyleCommands.CreateStyleCommand} -> 调用应用服务创建 -> 返回新风格档案。</p>
     * <p><b>关键调用：</b>{@code styleApplicationService.createStyle(...)}。</p>
     * <p><b>ID 语义：</b>projectId、operatorId 均为业务语义 ID。</p>
     * <p><b>异常与分支：</b>风格名冲突、参数非法或权限不足时返回业务异常。</p>
     * <p><b>副作用：</b>新增风格记录，可能变更默认风格。</p>
     */
    @PostMapping
    public ApiResponse<StyleProfile> createStyle(@PathVariable Long projectId,
                                                 @Valid @RequestBody CreateStyleDto dto,
                                                 @RequestParam("operatorId") Long operatorId,
                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(styleApplicationService.createStyle(
                projectId,
                new StyleCommands.CreateStyleCommand(
                        dto.getName(),
                        dto.getIsDefault(),
                        dto.getPace(),
                        dto.getTone(),
                        dto.getNarrativeFocus(),
                        dto.getPromptTemplate(),
                        dto.getSampleText(),
                        operatorId
                ),
                traceId
        ), traceId);
    }

    /**
     * 查询风格详情。
     * <p><b>业务目的：</b>返回指定风格完整配置，供编辑面板回填。</p>
     * <p><b>流程主线：</b>接收风格业务 ID -> 调用应用服务查询详情 -> 返回风格对象。</p>
     * <p><b>关键调用：</b>{@code styleApplicationService.getStyle(projectId, styleId)}。</p>
     * <p><b>ID 语义：</b>projectId、styleId 均为业务语义 ID。</p>
     * <p><b>异常与分支：</b>风格不存在或不属于项目时返回业务异常。</p>
     * <p><b>副作用：</b>无持久化写入。</p>
     */
    @GetMapping("/{styleId}")
    public ApiResponse<StyleProfile> getStyle(@PathVariable Long projectId,
                                              @PathVariable Long styleId,
                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(styleApplicationService.getStyle(projectId, styleId), traceId);
    }

    /**
     * 更新风格档案。
     * <p><b>业务目的：</b>修改已有风格配置，使写作风格约束随业务需求演进。</p>
     * <p><b>流程主线：</b>读取风格标识 -> 组装 {@link StyleCommands.UpdateStyleCommand} -> 调用应用服务更新 -> 返回最新风格。</p>
     * <p><b>关键调用：</b>{@code styleApplicationService.updateStyle(...)}。</p>
     * <p><b>ID 语义：</b>projectId、styleId、operatorId 均为业务语义 ID。</p>
     * <p><b>异常与分支：</b>风格不存在、参数越界或权限不足时返回业务异常。</p>
     * <p><b>副作用：</b>更新风格配置。</p>
     */
    @PutMapping("/{styleId}")
    public ApiResponse<StyleProfile> updateStyle(@PathVariable Long projectId,
                                                 @PathVariable Long styleId,
                                                 @Valid @RequestBody UpdateStyleDto dto,
                                                 @RequestParam("operatorId") Long operatorId,
                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(styleApplicationService.updateStyle(
                projectId,
                styleId,
                new StyleCommands.UpdateStyleCommand(
                        dto.getName(),
                        dto.getPace(),
                        dto.getTone(),
                        dto.getNarrativeFocus(),
                        dto.getPromptTemplate(),
                        dto.getSampleText(),
                        operatorId
                ),
                traceId
        ), traceId);
    }

    /**
     * 删除风格档案。
     * <p><b>业务目的：</b>移除无效或废弃风格，避免继续被章节或任务使用。</p>
     * <p><b>流程主线：</b>接收风格业务 ID 与操作者业务 ID -> 调用应用服务删除风格 -> 返回确认结果。</p>
     * <p><b>关键调用：</b>{@code styleApplicationService.deleteStyle(projectId, styleId, operatorId, traceId)}。</p>
     * <p><b>ID 语义：</b>projectId、styleId、operatorId 均为业务语义 ID。</p>
     * <p><b>异常与分支：</b>风格被引用、默认风格约束冲突或权限不足时返回业务异常。</p>
     * <p><b>副作用：</b>删除风格记录或标记失效。</p>
     */
    @DeleteMapping("/{styleId}")
    public ApiResponse<String> deleteStyle(@PathVariable Long projectId,
                                           @PathVariable Long styleId,
                                           @RequestParam("operatorId") Long operatorId,
                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        styleApplicationService.deleteStyle(projectId, styleId, operatorId, traceId);
        return ApiResponse.success("deleted", traceId);
    }

    /**
     * 切换项目当前默认风格。
     * <p><b>业务目的：</b>将写作默认风格切换到目标风格，影响后续生成任务的风格约束。</p>
     * <p><b>流程主线：</b>校验切换参数 -> 组装 {@link StyleCommands.SwitchStyleCommand} -> 调用应用服务切换 -> 返回新默认风格。</p>
     * <p><b>关键调用：</b>{@code styleApplicationService.switchStyle(...)}。</p>
     * <p><b>ID 语义：</b>projectId、toStyleId、operatorId 均为业务语义 ID。</p>
     * <p><b>异常与分支：</b>目标风格不可用、风险确认缺失或权限不足时返回业务异常。</p>
     * <p><b>副作用：</b>更新默认风格指向。</p>
     */
    @PostMapping("/switch")
    public ApiResponse<StyleProfile> switchStyle(@PathVariable Long projectId,
                                                 @Valid @RequestBody SwitchStyleDto dto,
                                                 @RequestParam("operatorId") Long operatorId,
                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(styleApplicationService.switchStyle(
                projectId,
                new StyleCommands.SwitchStyleCommand(
                        dto.getToStyleId(),
                        dto.getWarningConfirmed(),
                        dto.getReason(),
                        operatorId
                ),
                traceId
        ), traceId);
    }

    /**
     * 分析样本文本风格特征。
     * <p><b>业务目的：</b>从样本文本提取风格特征，辅助用户快速构建风格配置。</p>
     * <p><b>流程主线：</b>接收样本文本 -> 组装 {@link StyleCommands.AnalyzeStyleCommand} -> 调用应用服务分析 -> 返回分析结果。</p>
     * <p><b>关键调用：</b>{@code styleApplicationService.analyzeSample(...)}。</p>
     * <p><b>ID 语义：</b>projectId、operatorId 均为业务语义 ID。</p>
     * <p><b>异常与分支：</b>样本为空、文本过短或分析服务异常时返回业务异常。</p>
     * <p><b>副作用：</b>无持久化写入。</p>
     */
    @PostMapping("/analyze-sample")
    public ApiResponse<Map<String, Object>> analyzeSample(@PathVariable Long projectId,
                                                           @Valid @RequestBody AnalyzeStyleSampleDto dto,
                                                           @RequestParam("operatorId") Long operatorId,
                                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(styleApplicationService.analyzeSample(
                projectId,
                new StyleCommands.AnalyzeStyleCommand(dto.getSampleText(), operatorId),
                traceId
        ), traceId);
    }
}

