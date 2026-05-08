package com.penmate.backend.interfaces.api.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateAgentGenerationDto {

    @NotNull
    private Long conversationId;
    private Long chapterId;
    /**
     * 显式模型配置ID（完全显式模式下必填）。
     */
    @NotNull
    private Long modelConfigId;
    @NotBlank
    private String taskType;
    private String promptSnapshot;
    private String pluginSnapshot;
}
