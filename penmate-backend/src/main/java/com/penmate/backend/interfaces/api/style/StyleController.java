package com.penmate.backend.interfaces.api.style;

import com.penmate.backend.application.style.StyleApplicationService;
import com.penmate.backend.application.style.command.StyleCommands;
import com.penmate.backend.application.style.usecase.SessionStyleBindingAppService;
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
    private final SessionStyleBindingAppService sessionStyleBindingAppService;

    public StyleController(StyleApplicationService styleApplicationService,
                           SessionStyleBindingAppService sessionStyleBindingAppService) {
        this.styleApplicationService = styleApplicationService;
        this.sessionStyleBindingAppService = sessionStyleBindingAppService;
    }

    @GetMapping
    public ApiResponse<List<StyleProfile>> listStyles(@PathVariable Long projectId,
                                                      @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(styleApplicationService.listStyles(projectId), traceId);
    }

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

    @GetMapping("/{styleId}")
    public ApiResponse<StyleProfile> getStyle(@PathVariable Long projectId,
                                              @PathVariable Long styleId,
                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(styleApplicationService.getStyle(projectId, styleId), traceId);
    }

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

    @DeleteMapping("/{styleId}")
    public ApiResponse<String> deleteStyle(@PathVariable Long projectId,
                                           @PathVariable Long styleId,
                                           @RequestParam("operatorId") Long operatorId,
                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        styleApplicationService.deleteStyle(projectId, styleId, operatorId, traceId);
        return ApiResponse.success("deleted", traceId);
    }

    /**
     * 切换项目当前默认风格，并显式绑定到目标会话。
     */
    @PostMapping("/switch")
    public ApiResponse<StyleProfile> switchStyle(@PathVariable Long projectId,
                                                 @Valid @RequestBody SwitchStyleDto dto,
                                                 @RequestParam("operatorId") Long operatorId,
                                                 @RequestParam("sessionId") Long sessionId,
                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        StyleProfile switchedStyle = styleApplicationService.switchStyle(
                projectId,
                new StyleCommands.SwitchStyleCommand(
                        dto.getToStyleId(),
                        dto.getWarningConfirmed(),
                        dto.getReason(),
                        operatorId
                ),
                traceId
        );
        if (switchedStyle != null && switchedStyle.getStyleId() != null) {
            sessionStyleBindingAppService.bind(projectId, sessionId, switchedStyle.getStyleId(), operatorId, traceId);
        }
        return ApiResponse.success(switchedStyle, traceId);
    }

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
