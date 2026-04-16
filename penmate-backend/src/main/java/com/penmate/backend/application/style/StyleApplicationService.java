package com.penmate.backend.application.style;

import com.penmate.backend.application.style.command.StyleCommands.AnalyzeStyleCommand;
import com.penmate.backend.application.style.command.StyleCommands.CreateStyleCommand;
import com.penmate.backend.application.style.command.StyleCommands.SwitchStyleCommand;
import com.penmate.backend.application.style.command.StyleCommands.UpdateStyleCommand;
import com.penmate.backend.domain.shared.service.AuditService;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import com.penmate.backend.domain.style.model.StyleProfile;
import com.penmate.backend.domain.style.repository.StyleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 风格应用服务。
 * <p>负责项目风格档案的增删改查、默认风格切换与样本文本分析。</p>
 */
@Service
@Slf4j
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
     * 查询项目风格列表。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<StyleProfile> listStyles(Long projectId) {
        List<StyleProfile> styles = styleRepository.findByProjectId(projectId);
        log.info("查询风格列表: projectId={}, count={}", projectId, styles.size());
        return styles;
    }

    /**
     * 查询单个风格详情。
     *
     * @param projectId 入参：projectId
     * @param styleId 入参：styleId
     * @return 出参：处理结果
     */
    public StyleProfile getStyle(Long projectId, Long styleId) {
        log.info("查询风格详情: projectId={}, styleId={}", projectId, styleId);
        StyleProfile style = styleRepository.findById(projectId, styleId);
        if (style == null) {
            log.warn("查询风格详情失败: projectId={}, styleId={}, reason=not_found", projectId, styleId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Style not found");
        }
        log.info("查询风格详情成功: projectId={}, styleId={}, name={}", projectId, styleId, style.getName());
        return style;
    }

    /**
     * 创建项目风格档案。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public StyleProfile createStyle(Long projectId, CreateStyleCommand command, String traceId) {
        log.info("创建风格: projectId={}, name={}, isDefault={}, operatorId={}",
                projectId, command.name(), command.isDefault(), command.operatorId());
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
            log.error("创建风格失败: projectId={}, name={}, reason=insert_failed", projectId, command.name());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create style");
        }

        writeAudit(traceId, command.operatorId(), "style", "create-style", "style_profiles", String.valueOf(style.getId()), command.name(), 201);
        log.info("创建风格成功: projectId={}, styleId={}, name={}", projectId, style.getId(), style.getName());
        return style;
    }

    /**
     * 更新指定风格档案。
     *
     * @param projectId 入参：projectId
     * @param styleId 入参：styleId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public StyleProfile updateStyle(Long projectId, Long styleId, UpdateStyleCommand command, String traceId) {
        log.info("更新风格: projectId={}, styleId={}, operatorId={}", projectId, styleId, command.operatorId());
        StyleProfile style = getStyle(projectId, styleId);
        style.setName(command.name());
        style.setPace(command.pace());
        style.setTone(command.tone());
        style.setNarrativeFocus(command.narrativeFocus());
        style.setPromptTemplate(command.promptTemplate());
        style.setSampleText(command.sampleText());

        int affected = styleRepository.update(style);
        if (affected != 1) {
            log.error("更新风格失败: projectId={}, styleId={}, reason=update_failed", projectId, styleId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to update style");
        }

        writeAudit(traceId, command.operatorId(), "style", "update-style", "style_profiles", String.valueOf(styleId), command.name(), 200);
        log.info("更新风格成功: projectId={}, styleId={}, name={}", projectId, styleId, style.getName());
        return getStyle(projectId, styleId);
    }

    /**
     * 删除指定风格档案。
     *
     * @param projectId 入参：projectId
     * @param styleId 入参：styleId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void deleteStyle(Long projectId, Long styleId, Long operatorId, String traceId) {
        log.info("删除风格: projectId={}, styleId={}, operatorId={}", projectId, styleId, operatorId);
        int affected = styleRepository.softDelete(projectId, styleId);
        if (affected != 1) {
            log.warn("删除风格失败: projectId={}, styleId={}, reason=not_found", projectId, styleId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Style not found");
        }
        writeAudit(traceId, operatorId, "style", "delete-style", "style_profiles", String.valueOf(styleId), null, 200);
        log.info("删除风格成功: projectId={}, styleId={}", projectId, styleId);
    }

    /**
     * 切换项目默认风格。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public StyleProfile switchStyle(Long projectId, SwitchStyleCommand command, String traceId) {
        log.info("切换默认风格: projectId={}, toStyleId={}, operatorId={}", projectId, command.toStyleId(), command.operatorId());
        StyleProfile toStyle = getStyle(projectId, command.toStyleId());
        StyleProfile fromStyle = styleRepository.findDefaultByProjectId(projectId);

        styleRepository.clearDefaultByProjectId(projectId);
        int setAffected = styleRepository.setDefault(projectId, command.toStyleId());
        if (setAffected != 1) {
            log.error("切换默认风格失败: projectId={}, toStyleId={}, reason=set_default_failed", projectId, command.toStyleId());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to switch default style");
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
        log.info("切换默认风格成功: projectId={}, fromStyleId={}, toStyleId={}",
                projectId, fromStyle == null ? null : fromStyle.getId(), toStyle.getId());
        return getStyle(projectId, command.toStyleId());
    }

    /**
     * 分析样本文本并返回风格建议。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    public Map<String, Object> analyzeSample(Long projectId, AnalyzeStyleCommand command, String traceId) {
        log.info("分析风格样本: projectId={}, operatorId={}, sampleLength={}",
                projectId, command.operatorId(), command.sampleText() == null ? 0 : command.sampleText().length());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("suggestedTone", "balanced");
        result.put("suggestedPace", "medium");
        result.put("keywords", List.of("叙事", "节奏", "人物"));
        result.put("sampleLength", command.sampleText() == null ? 0 : command.sampleText().length());

        writeAudit(traceId, command.operatorId(), "style", "analyze-sample", "style_profiles", String.valueOf(projectId), command.sampleText(), 200);
        log.info("分析风格样本完成: projectId={}, suggestedTone={}, suggestedPace={}",
                projectId, result.get("suggestedTone"), result.get("suggestedPace"));
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


