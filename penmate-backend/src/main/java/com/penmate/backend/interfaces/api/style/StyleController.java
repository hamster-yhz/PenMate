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
import org.springframework.security.core.Authentication;
import static com.penmate.backend.interfaces.api.common.AuthenticatedActor.id;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 写作风格配置控制器�?
 * <p>负责项目风格档案的增删改查、默认风格切换与样本文本风格分析�?/p>
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
    public ApiResponse<List<Map<String, Object>>> listStyles(@PathVariable String projectId,
                                                             @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(styleApplicationService.listStyles(requireLongId(projectId, "projectId")).stream().map(this::toStyleView).toList(), traceId);
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> createStyle(@PathVariable String projectId,
                                                        @Valid @RequestBody CreateStyleDto dto,
                                                        Authentication authentication,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(toStyleView(styleApplicationService.createStyle(
                requireLongId(projectId, "projectId"),
                new StyleCommands.CreateStyleCommand(
                        dto.getName(),
                        dto.getIsDefault(),
                        dto.getPace(),
                        dto.getTone(),
                        dto.getNarrativeFocus(),
                        dto.getPromptTemplate(),
                        dto.getSampleText(),
                        id(authentication)
                ),
                traceId
        )), traceId);
    }

    @GetMapping("/{styleId}")
    public ApiResponse<Map<String, Object>> getStyle(@PathVariable String projectId,
                                                     @PathVariable String styleId,
                                                     @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(toStyleView(styleApplicationService.getStyle(requireLongId(projectId, "projectId"), requireLongId(styleId, "styleId"))), traceId);
    }

    @PutMapping("/{styleId}")
    public ApiResponse<Map<String, Object>> updateStyle(@PathVariable String projectId,
                                                        @PathVariable String styleId,
                                                        @Valid @RequestBody UpdateStyleDto dto,
                                                        Authentication authentication,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(toStyleView(styleApplicationService.updateStyle(
                requireLongId(projectId, "projectId"),
                requireLongId(styleId, "styleId"),
                new StyleCommands.UpdateStyleCommand(
                        dto.getName(),
                        dto.getPace(),
                        dto.getTone(),
                        dto.getNarrativeFocus(),
                        dto.getPromptTemplate(),
                        dto.getSampleText(),
                        id(authentication)
                ),
                traceId
        )), traceId);
    }

    @DeleteMapping("/{styleId}")
    public ApiResponse<String> deleteStyle(@PathVariable String projectId,
                                           @PathVariable String styleId,
                                           Authentication authentication,
                                           @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        styleApplicationService.deleteStyle(requireLongId(projectId, "projectId"), requireLongId(styleId, "styleId"), id(authentication), traceId);
        return ApiResponse.success("deleted", traceId);
    }

    /**
     * 切换项目当前默认风格，并显式绑定到目标会话�?
     */
    @PostMapping("/switch")
    public ApiResponse<Map<String, Object>> switchStyle(@PathVariable String projectId,
                                                        @Valid @RequestBody SwitchStyleDto dto,
                                                        Authentication authentication,
                                                        @RequestParam("sessionId") String sessionId,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Long parsedProjectId = requireLongId(projectId, "projectId");
        Long parsedOperatorId = id(authentication);
        Long parsedSessionId = requireLongId(sessionId, "sessionId");
        StyleProfile switchedStyle = styleApplicationService.switchStyle(
                parsedProjectId,
                new StyleCommands.SwitchStyleCommand(
                        requireLongId(dto.getToStyleId(), "toStyleId"),
                        dto.getWarningConfirmed(),
                        dto.getReason(),
                        parsedOperatorId
                ),
                traceId
        );
        if (switchedStyle != null && switchedStyle.getStyleId() != null) {
            sessionStyleBindingAppService.bind(parsedProjectId, parsedSessionId, switchedStyle.getStyleId(), parsedOperatorId, traceId);
        }
        return ApiResponse.success(toStyleView(switchedStyle), traceId);
    }

    @PostMapping("/analyze-sample")
    public ApiResponse<Map<String, Object>> analyzeSample(@PathVariable String projectId,
                                                          @Valid @RequestBody AnalyzeStyleSampleDto dto,
                                                          Authentication authentication,
                                                          @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(styleApplicationService.analyzeSample(
                requireLongId(projectId, "projectId"),
                new StyleCommands.AnalyzeStyleCommand(dto.getSampleText(), id(authentication)),
                traceId
        ), traceId);
    }

    private Map<String, Object> toStyleView(StyleProfile styleProfile) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("styleId", styleProfile == null ? null : stringifyBusinessId(styleProfile.getStyleId()));
        data.put("projectId", styleProfile == null ? null : stringifyBusinessId(styleProfile.getProjectId()));
        data.put("name", styleProfile == null ? null : styleProfile.getName());
        data.put("isDefault", styleProfile == null ? null : styleProfile.getIsDefault());
        data.put("pace", styleProfile == null ? null : styleProfile.getPace());
        data.put("tone", styleProfile == null ? null : styleProfile.getTone());
        data.put("narrativeFocus", styleProfile == null ? null : styleProfile.getNarrativeFocus());
        data.put("promptTemplate", styleProfile == null ? null : styleProfile.getPromptTemplate());
        data.put("sampleText", styleProfile == null ? null : styleProfile.getSampleText());
        data.put("createdAt", styleProfile == null ? null : styleProfile.getCreatedAt());
        data.put("updatedAt", styleProfile == null ? null : styleProfile.getUpdatedAt());
        return data;
    }

    private Long requireLongId(String rawValue, String fieldName) {
        String normalized = Objects.requireNonNull(rawValue, fieldName + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (!normalized.matches("^\\d+$")) {
            throw new IllegalArgumentException(fieldName + " must be a numeric string business id");
        }
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a valid numeric string business id", ex);
        }
    }

    private String stringifyBusinessId(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}


