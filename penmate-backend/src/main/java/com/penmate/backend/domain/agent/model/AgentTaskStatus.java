package com.penmate.backend.domain.agent.model;

import java.util.Arrays;

/**
 * 智能体生成任务状态枚举。
 */
public enum AgentTaskStatus {
    /** 已创建，等待调度执行。 */
    PENDING("pending"),
    /** 正在执行中。 */
    RUNNING("running"),
    /** 进入人工审批等待状态。 */
    WAITING_APPROVAL("waiting_approval"),
    /** 执行完成且生成内容可用。 */
    DONE("done"),
    /** 生成结果已被应用到业务对象。 */
    APPLIED("applied"),
    /** 执行失败。 */
    FAILED("failed"),
    /** 任务被主动取消。 */
    CANCELLED("cancelled");

    /** 持久化层存储的状态值。 */
    private final String value;

    AgentTaskStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * 根据字符串状态值解析枚举。
     *
     * @param raw 原始状态值
     * @return 匹配到的任务状态；未匹配返回 {@code null}
     */
    public static AgentTaskStatus fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> status.value.equalsIgnoreCase(raw))
                .findFirst()
                .orElse(null);
    }
}

