package com.penmate.backend.interfaces.api.agent.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 创建 agent turn 的请求 DTO。
 * <p>该结构用于承接“用户消息 + 任务请求上下文”的统一提交，
 * Replaces the legacy split request pattern with a single run-creating turn request.</p>
 */
@Data
public class CreateAgentTurnDto {

    /**
     * 发起当前轮次的操作人业务 ID。
     */
    /**
     * 用户输入的主消息内容。
     */
    @NotBlank(message = "userMessage must not be blank")
    private String userMessage;

    @NotNull(message = "activeSkills must not be null")
    private List<String> activeSkills;

    /**
     * 当前轮次要启动的任务请求。
     */
    @Valid
    @NotNull(message = "taskRequest must not be null")
    private TaskRequest taskRequest;

    /**
     * 任务请求最小契约。
     */
    @Data
    public static class TaskRequest {

        /**
         * 任务类型，例如 WRITE。
         */
        @NotBlank(message = "taskType must not be blank")
        private String taskType;

        /**
         * 关联章节业务 ID。
         */
        private String chapterId;

        /**
         * 当前轮次显式模型配置业务 ID。
         */
        private String modelConfigId;

        /**
         * 当前选中文本快照。
         */
        private String selectedText;
    }
}
