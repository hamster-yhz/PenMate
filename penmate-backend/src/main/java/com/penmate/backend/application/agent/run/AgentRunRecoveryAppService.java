package com.penmate.backend.application.agent.run;

import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
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

    public AgentRunRecoveryAppService(AgentSessionRepository agentSessionRepository,
                                      AgentRunProjectionRepository agentRunProjectionRepository) {
        this.agentSessionRepository = agentSessionRepository;
        this.agentRunProjectionRepository = agentRunProjectionRepository;
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
        List<Object> messages = agentSessionRepository.listMessageRows(sessionId).stream()
                .<Object>map(LinkedHashMap::new)
                .toList();
        Map<String, Object> workbenchContext = new LinkedHashMap<>();
        if (activeRun != null) {
            workbenchContext.put("activeRun", Map.of(
                    "runId", String.valueOf(activeRun.runId()),
                    "runStatus", activeRun.runStatus(),
                    "runPhase", activeRun.runPhase(),
                    "latestSequence", String.valueOf(activeRun.latestSequence())
            ));
        }
        return new AgentRunRecoveryResult(
                new AgentRunRecoveryResult.SessionView(
                        session.getSessionId(),
                        session.getTitle(),
                        session.getSessionStatus(),
                        session.getBoundStyleId() == null
                                ? null
                                : new AgentRunRecoveryResult.BoundStyleView(session.getBoundStyleId(), null),
                        activeRun == null ? session.getLastRunStatus() : activeRun.runStatus()
                ),
                activeRun,
                null,
                messages,
                workbenchContext.isEmpty() ? null : workbenchContext
        );
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
