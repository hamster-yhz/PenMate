package com.penmate.backend.application.agent;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.model.AgentTaskStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * 统一的生成任务状态机守卫。
 */
@Component
public class AgentTaskStateMachine {

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

    /**
     * 断言状态迁移是否合法。
     * <p>用于所有状态写库前的统一守卫，不合法直接抛业务异常。</p>
     */
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

    /**
     * 解析状态字符串为枚举。
     * <p>用于把数据库字符串状态转换为类型安全的状态值。</p>
     */
    public AgentTaskStatus parseStatus(String statusRaw) {
        AgentTaskStatus status = AgentTaskStatus.fromValue(statusRaw);
        if (status == null) {
            throw invalidTransition(statusRaw, null);
        }
        return status;
    }

    /**
     * 统一构造非法状态迁移异常。
     */
    private BusinessException invalidTransition(String from, String to) {
        return BusinessException.of(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "AGENT_STATE_TRANSITION_INVALID",
                "Invalid generation task state transition",
                Map.of("from", String.valueOf(from), "to", String.valueOf(to))
        );
    }
}

