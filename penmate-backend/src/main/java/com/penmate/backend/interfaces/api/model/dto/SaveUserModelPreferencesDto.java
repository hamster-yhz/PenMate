package com.penmate.backend.interfaces.api.model.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 保存用户模型偏好入参。
 * <p>用于维护用户在主 Agent 与脏活 Agent 场景下的默认模型配置选择。</p>
 */
@Data
public class SaveUserModelPreferencesDto {

    /** 主 Agent 默认模型配置 ID，传空表示清空。 */
    @Positive(message = "mainAgentModelConfigId must be greater than 0")
    private Long mainAgentModelConfigId;

    /** 脏活 Agent 默认模型配置 ID，传空表示清空。 */
    @Positive(message = "dirtyWorkAgentModelConfigId must be greater than 0")
    private Long dirtyWorkAgentModelConfigId;
}
