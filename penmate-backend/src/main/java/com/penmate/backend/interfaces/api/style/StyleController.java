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
 * StyleController。
 * <p>控制层：负责HTTP请求接入、参数校验与统一响应封装。</p>
 */
@RestController
@RequestMapping("/api/v1/novels/{projectId}/styles")
public class StyleController {

    private final StyleApplicationService styleApplicationService;

    public StyleController(StyleApplicationService styleApplicationService) {
        this.styleApplicationService = styleApplicationService;
    }

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping
    public ApiResponse<List<StyleProfile>> listStyles(@PathVariable Long projectId,
                                                      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(styleApplicationService.listStyles(projectId), traceId);
    }

    /**
     * 创建业务数据。
     *
     * @param projectId 入参：projectId
     * @param dto 入参：dto
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
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
     * 查询详情数据。
     *
     * @param projectId 入参：projectId
     * @param styleId 入参：styleId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/{styleId}")
    public ApiResponse<StyleProfile> getStyle(@PathVariable Long projectId,
                                              @PathVariable Long styleId,
                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(styleApplicationService.getStyle(projectId, styleId), traceId);
    }

    /**
     * 更新业务数据。
     *
     * @param projectId 入参：projectId
     * @param styleId 入参：styleId
     * @param dto 入参：dto
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
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
     * 删除业务数据。
     *
     * @param projectId 入参：projectId
     * @param styleId 入参：styleId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
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
     * 切换业务状态。
     *
     * @param projectId 入参：projectId
     * @param dto 入参：dto
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
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
     * 分析输入内容。
     *
     * @param projectId 入参：projectId
     * @param dto 入参：dto
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
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

