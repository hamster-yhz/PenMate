package com.penmate.backend.domain.agent.service;

import com.penmate.backend.domain.agent.model.AgentTaskStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * 统一的生成任务状态迁移策略。
 */
public class AgentTaskTransitionPolicy {

    private static final Map<AgentTaskStatus, EnumSet<AgentTaskStatus>> ALLOWED_TRANSITIONS =
            new EnumMap<>(AgentTaskStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(AgentTaskStatus.PENDING,
                EnumSet.of(AgentTaskStatus.RUNNING, AgentTaskStatus.CANCELLED, AgentTaskStatus.FAILED));
        ALLOWED_TRANSITIONS.put(AgentTaskStatus.RUNNING,
                EnumSet.of(AgentTaskStatus.WAITING_APPROVAL, AgentTaskStatus.DONE, AgentTaskStatus.FAILED, AgentTaskStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(AgentTaskStatus.WAITING_APPROVAL,
                EnumSet.of(AgentTaskStatus.RUNNING, AgentTaskStatus.DONE, AgentTaskStatus.FAILED, AgentTaskStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(AgentTaskStatus.DONE, EnumSet.of(AgentTaskStatus.APPLIED));
        ALLOWED_TRANSITIONS.put(AgentTaskStatus.APPLIED, EnumSet.noneOf(AgentTaskStatus.class));
        ALLOWED_TRANSITIONS.put(AgentTaskStatus.FAILED, EnumSet.noneOf(AgentTaskStatus.class));
        ALLOWED_TRANSITIONS.put(AgentTaskStatus.CANCELLED, EnumSet.noneOf(AgentTaskStatus.class));
    }

    public void assertTransition(String fromRaw, AgentTaskStatus toStatus) {
        AgentTaskStatus fromStatus = AgentTaskStatus.fromValue(fromRaw);
        if (fromStatus == null || toStatus == null) {
            throw invalidTransition(fromRaw, toStatus == null ? null : toStatus.value());
        }
        if (fromStatus == toStatus) {
            return;
        }
        EnumSet<AgentTaskStatus> next = ALLOWED_TRANSITIONS.get(fromStatus);
        if (next == null || !next.contains(toStatus)) {
            throw invalidTransition(fromStatus.value(), toStatus.value());
        }
    }

    public AgentTaskStatus parseStatus(String statusRaw) {
        AgentTaskStatus status = AgentTaskStatus.fromValue(statusRaw);
        if (status == null) {
            throw invalidTransition(statusRaw, null);
        }
        return status;
    }

    private InvalidAgentTaskTransitionException invalidTransition(String from, String to) {
        return new InvalidAgentTaskTransitionException("Invalid generation task state transition");
    }
}
