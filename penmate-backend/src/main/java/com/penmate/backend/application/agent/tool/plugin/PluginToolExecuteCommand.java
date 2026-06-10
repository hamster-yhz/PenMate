package com.penmate.backend.application.agent.tool.plugin;

/**
 * 插件型 tool 执行命令。
 */
public record PluginToolExecuteCommand(
        Long projectId,
        Long runId,
        String prompt,
        String traceId
) {
}
