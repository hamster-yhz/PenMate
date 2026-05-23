package com.penmate.backend.application.agent.orchestration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.context.ContextPackage;
import com.penmate.backend.application.agent.orchestration.profile.TaskProfile;
import com.penmate.backend.application.agent.prompt.PromptPlan;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Agent 任务运行时统计更新器。
 * <p>负责在生成完成后回写估算成本与结构化快照，不覆盖 tool loop 已持久化的真实 token 使用量。</p>
 */
@Component
@RequiredArgsConstructor
public class AgentTaskRuntimeUpdater {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AgentRepository agentRepository;

    public void updateGenerationRuntime(Long projectId,
                                        Long taskId,
                                        String promptSnapshot,
                                        String generatedText,
                                        String traceId) {
        String costJson = "{\"currency\":\"USD\",\"estimated\":"
                + String.format(Locale.ROOT, "%.6f", estimateCost(generatedText)) + "}";
        int affected = agentRepository.updateGenerationTaskRuntime(projectId, taskId, null, costJson, traceId);
        if (affected != 1) {
            throw new IllegalStateException("Failed to update generation task runtime");
        }
    }

    public void updateGenerationRuntime(Long projectId,
                                        Long taskId,
                                        String promptSnapshot,
                                        String generatedText,
                                        String traceId,
                                        TaskProfile taskProfile,
                                        PromptPlan promptPlan,
                                        ContextPackage contextPackage) {
        updateGenerationRuntime(projectId, taskId, promptSnapshot, generatedText, traceId);
        persistTaskSnapshots(projectId, taskId, null, taskProfile, promptPlan, contextPackage);
    }

    public void updateGenerationRuntime(Long projectId,
                                        Long taskId,
                                        String promptSnapshot,
                                        String generatedText,
                                        String traceId,
                                        AgentTaskContext taskContext,
                                        TaskProfile taskProfile,
                                        PromptPlan promptPlan,
                                        ContextPackage contextPackage,
                                        String activeToolCallsSnapshot,
                                        String lastRuntimeStatus,
                                        String recoveryCursor) {
        updateGenerationRuntime(projectId, taskId, promptSnapshot, generatedText, traceId);
        if (taskContext != null) {
            taskContext.setActiveToolCallsSnapshot(activeToolCallsSnapshot);
            taskContext.setLastRuntimeStatus(lastRuntimeStatus);
            taskContext.setRecoveryCursor(recoveryCursor);
        }
        persistTaskSnapshots(projectId, taskId, taskContext, taskProfile, promptPlan, contextPackage);
    }

    public void persistTaskSnapshots(Long projectId,
                                     Long taskId,
                                     AgentTaskContext taskContext,
                                     TaskProfile taskProfile,
                                     PromptPlan promptPlan,
                                     ContextPackage contextPackage) {
        String taskProfileJson = toSnapshotJson(taskProfile);
        String promptPlanJson = toSnapshotJson(promptPlan);
        String contextPackageJson = toSnapshotJson(contextPackage);
        String activeToolCallsSnapshot = taskContext == null ? null : taskContext.getActiveToolCallsSnapshot();
        String lastRuntimeStatus = taskContext == null ? null : taskContext.getLastRuntimeStatus();
        String recoveryCursor = taskContext == null ? null : taskContext.getRecoveryCursor();
        if (taskContext != null) {
            taskContext.setTaskProfileJson(taskProfileJson);
            taskContext.setPromptPlanJson(promptPlanJson);
            taskContext.setContextPackageJson(contextPackageJson);
        }
        int affected = agentRepository.updateGenerationTaskSnapshots(
                projectId,
                taskId,
                taskProfileJson,
                promptPlanJson,
                contextPackageJson,
                activeToolCallsSnapshot,
                lastRuntimeStatus,
                recoveryCursor
        );
        if (affected != 1) {
            throw new IllegalStateException("Failed to update generation task snapshots");
        }
    }

    public static String toSnapshotJson(Object value) {
        Object payload = value;
        if (value instanceof TaskProfile taskProfile) {
            payload = serializeTaskProfile(taskProfile);
        } else if (value instanceof PromptPlan promptPlan) {
            payload = serializePromptPlan(promptPlan);
        } else if (value instanceof ContextPackage contextPackage) {
            payload = serializeContextPackage(contextPackage);
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize runtime snapshot", ex);
        }
    }

    private static Map<String, Object> serializeTaskProfile(TaskProfile taskProfile) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("intentTags", taskProfile == null ? null : taskProfile.intentTags());
        payload.put("executionProfile", taskProfile == null ? null : taskProfile.executionProfile());
        payload.put("skills", taskProfile == null ? null : taskProfile.skills());
        payload.put("tools", taskProfile == null ? null : taskProfile.tools());
        payload.put("hardConstraints", taskProfile == null ? null : taskProfile.hardConstraints());
        payload.put("outputExpectation", taskProfile == null ? null : taskProfile.outputExpectation());
        payload.put("needsApproval", taskProfile != null && taskProfile.needsApproval());
        payload.put("includeStoryBible", taskProfile != null && taskProfile.includeStoryBible());
        payload.put("includeRag", taskProfile != null && taskProfile.includeRag());
        payload.put("reasoningSummary", taskProfile == null ? null : taskProfile.reasoningSummary());
        return payload;
    }

    private static Map<String, Object> serializePromptPlan(PromptPlan promptPlan) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("modules", promptPlan == null ? null : promptPlan.modules());
        payload.put("skills", promptPlan == null ? null : promptPlan.skills());
        payload.put("finalProfile", promptPlan == null ? null : promptPlan.finalProfile());
        payload.put("assembledPromptPreview", promptPlan == null ? null : promptPlan.assembledPromptPreview());
        return payload;
    }

    private static Map<String, Object> serializeContextPackage(ContextPackage contextPackage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sources", contextPackage == null ? null : contextPackage.sources());
        payload.put("missingContextFlags", contextPackage == null ? null : contextPackage.missingContextFlags());
        payload.put("conflicts", contextPackage == null ? null : contextPackage.conflicts());
        payload.put("storyBibleEntries", contextPackage == null ? null : contextPackage.storyBibleEntries());
        payload.put("ragRefs", contextPackage == null ? null : contextPackage.ragRefs());
        payload.put("styleSnapshot", contextPackage == null ? null : contextPackage.styleSnapshot());
        payload.put("chapterScope", contextPackage == null ? null : contextPackage.chapterScope());
        return payload;
    }

    private int safeLength(String text) {
        return text == null ? 0 : text.length();
    }

    private double estimateCost(String generatedText) {
        return safeLength(generatedText) * 0.000002D;
    }
}
