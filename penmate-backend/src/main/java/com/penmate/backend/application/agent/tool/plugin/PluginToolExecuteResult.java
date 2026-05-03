package com.penmate.backend.application.agent.tool.plugin;

public record PluginToolExecuteResult(
        String pluginCode,
        String toolName,
        String output,
        boolean success,
        String errorMsg
) {
    public static PluginToolExecuteResult success(String pluginCode, String toolName, String output) {
        return new PluginToolExecuteResult(pluginCode, toolName, output, true, null);
    }

    public static PluginToolExecuteResult failed(String pluginCode, String toolName, String errorMsg) {
        return new PluginToolExecuteResult(pluginCode, toolName, "", false, errorMsg);
    }
}
