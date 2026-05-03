package com.penmate.backend.application.agent.tool.plugin;

public record PluginToolExecuteCommand(
        Long projectId,
        Long taskId,
        String prompt,
        String traceId
) {
}
