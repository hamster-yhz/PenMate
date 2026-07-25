package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.agent.tool.runtime.ToolApprovalPreview;
import com.penmate.backend.application.agent.skill.AgentSkillActivationService;
import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunProjectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AgentRunRecoveryAppService {

    private final AgentSessionRepository agentSessionRepository;
    private final AgentRunProjectionRepository agentRunProjectionRepository;
    private final AgentRunPendingApprovalRepository pendingApprovals;
    private final AgentPartialMessageCheckpointStore partialMessages;
    private final ToolApprovalPreview toolApprovalPreview;
    private final AgentSkillActivationService skillActivationService;

    public AgentRunRecoveryAppService(AgentSessionRepository agentSessionRepository,
                                      AgentRunProjectionRepository agentRunProjectionRepository,
                                      AgentRunPendingApprovalRepository pendingApprovals,
                                      AgentPartialMessageCheckpointStore partialMessages,
                                      ToolApprovalPreview toolApprovalPreview,
                                      AgentSkillActivationService skillActivationService) {
        this.agentSessionRepository = agentSessionRepository;
        this.agentRunProjectionRepository = agentRunProjectionRepository;
        this.pendingApprovals = pendingApprovals;
        this.partialMessages = partialMessages;
        this.toolApprovalPreview = toolApprovalPreview;
        this.skillActivationService = skillActivationService;
    }

    public AgentRunRecoveryResult getRecovery(Long projectId, Long sessionId, String traceId) {
        AgentRunRecoveryResult result = buildRecovery(projectId, sessionId);
        log.info("Agent run recovery resolved: projectId={}, sessionId={}, traceId={}, hasSession={}, activeRunId={}, activeRunStatus={}",
                projectId,
                sessionId,
                traceId,
                result.session() != null,
                result.activeRun() == null ? null : result.activeRun().runId(),
                result.activeRun() == null ? null : result.activeRun().runStatus());
        return result;
    }

    public AgentRunRecoveryResult resumeSession(Long projectId,
                                                Long sessionId,
                                                Long operatorId,
                                                String trigger,
                                                String traceId) {
        AgentRunRecoveryResult result = buildRecovery(projectId, sessionId);
        log.info("Agent run resume resolved: projectId={}, sessionId={}, operatorId={}, trigger={}, traceId={}, activeRunId={}, activeRunStatus={}",
                projectId,
                sessionId,
                operatorId,
                trigger,
                traceId,
                result.activeRun() == null ? null : result.activeRun().runId(),
                result.activeRun() == null ? null : result.activeRun().runStatus());
        return result;
    }

    private AgentRunRecoveryResult buildRecovery(Long projectId, Long sessionId) {
        AgentSession session = agentSessionRepository.findSession(projectId, sessionId);
        if (session == null) {
            return new AgentRunRecoveryResult(null, null, null, List.of(), null);
        }
        Map<String, Object> runProjection = agentRunProjectionRepository.findLatestRunForSession(projectId, sessionId);
        AgentRunRecoveryResult.ActiveRunView activeRun = toActiveRun(runProjection);
        List<Object> messages = new java.util.ArrayList<>(agentSessionRepository.listMessageRows(sessionId).stream()
                .<Object>map(LinkedHashMap::new)
                .toList());
        removeAmbiguousActiveTurnAssistant(messages, activeRun);
        appendPartialAssistantMessage(messages, activeRun);
        Map<String, Object> pendingApproval = null;
        if (activeRun != null && "WAITING_APPROVAL".equalsIgnoreCase(activeRun.runStatus())) {
            var pending = pendingApprovals.findPendingByRunId(activeRun.runId());
            if (pending != null) {
                pendingApproval = pendingApprovalView(pending);
                Map<String, Object> message = new LinkedHashMap<>(pendingApproval);
                message.put("messageId", "approval-" + pending.approvalId());
                message.put("turnId", String.valueOf(activeRun.turnId()));
                message.put("runId", String.valueOf(activeRun.runId()));
                message.put("role", "assistant");
                message.put("contentMarkdown", "");
                message.put("createdAt", pending.createdAt());
                messages.add(message);
            }
        }
        Map<String, Object> workbenchContext = new LinkedHashMap<>();
        if (activeRun != null) {
            Map<String, Object> activeRunContext = new LinkedHashMap<>();
            activeRunContext.put("runId", String.valueOf(activeRun.runId()));
            activeRunContext.put("runStatus", activeRun.runStatus());
            activeRunContext.put("runPhase", activeRun.runPhase());
            activeRunContext.put("latestSequence", String.valueOf(activeRun.latestSequence()));
            workbenchContext.put("activeRun", activeRunContext);
        }
        return new AgentRunRecoveryResult(
                new AgentRunRecoveryResult.SessionView(
                        session.getSessionId(),
                        session.getTitle(),
                        session.getSessionStatus(),
                        session.getBoundStyleId() == null
                                ? null
                                : new AgentRunRecoveryResult.BoundStyleView(session.getBoundStyleId(), null),
                        activeRun == null ? session.getLastRunStatus() : activeRun.runStatus(),
                        skillActivationService.listSessionSkills(sessionId)
                ),
                activeRun,
                pendingApproval,
                messages,
                workbenchContext.isEmpty() ? null : workbenchContext
        );
    }

    private void removeAmbiguousActiveTurnAssistant(List<Object> messages,
                                                    AgentRunRecoveryResult.ActiveRunView activeRun) {
        if (activeRun == null || activeRun.turnId() == null) return;
        String activeTurnId = String.valueOf(activeRun.turnId());
        messages.removeIf(message -> message instanceof Map<?, ?> row
                && "assistant".equalsIgnoreCase(stringValue(row.get("role")))
                && activeTurnId.equals(stringValue(row.get("turnId"))));
    }

    private void appendPartialAssistantMessage(List<Object> messages,
                                               AgentRunRecoveryResult.ActiveRunView activeRun) {
        if (activeRun == null || activeRun.runId() == null || activeRun.turnId() == null) return;
        boolean hasAssistantMessage = messages.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .anyMatch(message -> "assistant".equalsIgnoreCase(stringValue(message.get("role")))
                        && String.valueOf(activeRun.turnId()).equals(stringValue(message.get("turnId"))));
        if (hasAssistantMessage) return;
        partialMessages.find(activeRun.runId())
                .filter(snapshot -> !snapshot.text().isBlank())
                .ifPresent(snapshot -> {
                    Map<String, Object> message = new LinkedHashMap<>();
                    message.put("messageId", "partial-" + activeRun.runId());
                    message.put("turnId", String.valueOf(activeRun.turnId()));
                    message.put("runId", String.valueOf(activeRun.runId()));
                    message.put("role", "assistant");
                    message.put("contentMarkdown", snapshot.text());
                    message.put("partial", true);
                    message.put("createdAt", snapshot.updatedAt());
                    messages.add(message);
                });
    }

    private Map<String, Object> pendingApprovalView(AgentRunPendingApproval pending) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("approvalId", String.valueOf(pending.approvalId()));
        view.put("approvalStatus", "pending");
        view.put("toolCallId", pending.toolCallId());
        view.put("toolCode", pending.toolCode());
        view.put("approvalPreview", toolApprovalPreview.from(pending.toolCode(), pending.toolArgsJson()));
        return view;
    }

    private AgentRunRecoveryResult.ActiveRunView toActiveRun(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        return new AgentRunRecoveryResult.ActiveRunView(
                longValue(row.get("turnId")),
                longValue(row.get("runId")),
                stringValue(row.get("runStatus")),
                stringValue(row.get("runPhase")),
                longValue(row.get("latestSequence"))
        );
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
