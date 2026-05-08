package com.penmate.backend.interfaces.api.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 会话恢复请求 DTO。
 * <p>当前契约要求显式携带触发恢复的操作人与恢复触发源，
 * 以便后续应用层记录 resumed_at、审计日志与恢复来源。</p>
 */
@Data
public class ResumeAgentSessionDto {

    /**
     * 触发本次会话恢复的操作人业务 ID。
     */
    @NotNull(message = "operatorId must not be null")
    private Long operatorId;

    /**
     * 恢复触发来源，例如 WORKBENCH_ENTER。
     */
    @NotBlank(message = "trigger must not be blank")
    private String trigger;
}
