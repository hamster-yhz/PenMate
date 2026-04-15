package com.penmate.backend.application.style;

import com.penmate.backend.application.style.command.StyleCommands.AnalyzeStyleCommand;
import com.penmate.backend.application.style.command.StyleCommands.CreateStyleCommand;
import com.penmate.backend.application.style.command.StyleCommands.SwitchStyleCommand;
import com.penmate.backend.application.style.command.StyleCommands.UpdateStyleCommand;
import com.penmate.backend.domain.shared.service.AuditService;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import com.penmate.backend.domain.style.model.StyleProfile;
import com.penmate.backend.domain.style.repository.StyleRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * StyleApplicationService。
 * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
 */
@Service
public class StyleApplicationService {

    private final StyleRepository styleRepository;
    private final AuditService auditService;
    private final RealtimeEventService realtimeEventService;

    public StyleApplicationService(StyleRepository styleRepository,
                                   AuditService auditService,
                                   RealtimeEventService realtimeEventService) {
        this.styleRepository = styleRepository;
        this.auditService = auditService;
        this.realtimeEventService = realtimeEventService;
    }

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<StyleProfile> listStyles(Long projectId) {
        return styleRepository.findByProjectId(projectId);
    }

    /**
     * 查询详情数据。
     *
     * @param projectId 入参：projectId
     * @param styleId 入参：styleId
     * @return 出参：处理结果
     */
    public StyleProfile getStyle(Long projectId, Long styleId) {
        StyleProfile style = styleRepository.findById(projectId, styleId);
        if (style == null) {
            throw new IllegalArgumentException("Style not found");
        }
        return style;
    }

    /**
     * 创建业务数据。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public StyleProfile createStyle(Long projectId, CreateStyleCommand command, String traceId) {
        StyleProfile style = new StyleProfile();
        style.setProjectId(projectId);
        style.setName(command.name());
        style.setIsDefault(Boolean.TRUE.equals(command.isDefault()));
        style.setPace(command.pace());
        style.setTone(command.tone());
        style.setNarrativeFocus(command.narrativeFocus());
        style.setPromptTemplate(command.promptTemplate());
        style.setSampleText(command.sampleText());

        if (Boolean.TRUE.equals(style.getIsDefault())) {
            styleRepository.clearDefaultByProjectId(projectId);
        }

        int affected = styleRepository.insert(style);
        if (affected != 1) {
            throw new IllegalArgumentException("Failed to create style");
        }

        writeAudit(traceId, command.operatorId(), "style", "create-style", "style_profiles", String.valueOf(style.getId()), command.name(), 201);
        return style;
    }

    /**
     * 更新业务数据。
     *
     * @param projectId 入参：projectId
     * @param styleId 入参：styleId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public StyleProfile updateStyle(Long projectId, Long styleId, UpdateStyleCommand command, String traceId) {
        StyleProfile style = getStyle(projectId, styleId);
        style.setName(command.name());
        style.setPace(command.pace());
        style.setTone(command.tone());
        style.setNarrativeFocus(command.narrativeFocus());
        style.setPromptTemplate(command.promptTemplate());
        style.setSampleText(command.sampleText());

        int affected = styleRepository.update(style);
        if (affected != 1) {
            throw new IllegalArgumentException("Failed to update style");
        }

        writeAudit(traceId, command.operatorId(), "style", "update-style", "style_profiles", String.valueOf(styleId), command.name(), 200);
        return getStyle(projectId, styleId);
    }

    /**
     * 删除业务数据。
     *
     * @param projectId 入参：projectId
     * @param styleId 入参：styleId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void deleteStyle(Long projectId, Long styleId, Long operatorId, String traceId) {
        int affected = styleRepository.softDelete(projectId, styleId);
        if (affected != 1) {
            throw new IllegalArgumentException("Style not found");
        }
        writeAudit(traceId, operatorId, "style", "delete-style", "style_profiles", String.valueOf(styleId), null, 200);
    }

    /**
     * 切换业务状态。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public StyleProfile switchStyle(Long projectId, SwitchStyleCommand command, String traceId) {
        StyleProfile toStyle = getStyle(projectId, command.toStyleId());
        StyleProfile fromStyle = styleRepository.findDefaultByProjectId(projectId);

        styleRepository.clearDefaultByProjectId(projectId);
        int setAffected = styleRepository.setDefault(projectId, command.toStyleId());
        if (setAffected != 1) {
            throw new IllegalArgumentException("Failed to switch default style");
        }

        styleRepository.insertSwitchLog(
                projectId,
                fromStyle == null ? null : fromStyle.getId(),
                toStyle.getId(),
                command.operatorId(),
                Boolean.TRUE.equals(command.warningConfirmed()),
                command.reason()
        );

        realtimeEventService.publishProjectEvent(projectId, "style.switched", Map.of(
                "fromStyleId", fromStyle == null ? null : fromStyle.getId(),
                "toStyleId", toStyle.getId(),
                "operatorId", command.operatorId(),
                "reason", command.reason()
        ));

        writeAudit(traceId, command.operatorId(), "style", "switch-style", "style_profiles", String.valueOf(toStyle.getId()), command.reason(), 200);
        return getStyle(projectId, command.toStyleId());
    }

    /**
     * 分析输入内容。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public Map<String, Object> analyzeSample(Long projectId, AnalyzeStyleCommand command, String traceId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("suggestedTone", "balanced");
        result.put("suggestedPace", "medium");
        result.put("keywords", List.of("叙事", "节奏", "人物"));
        result.put("sampleLength", command.sampleText() == null ? 0 : command.sampleText().length());

        writeAudit(traceId, command.operatorId(), "style", "analyze-sample", "style_profiles", String.valueOf(projectId), command.sampleText(), 200);
        return result;
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

