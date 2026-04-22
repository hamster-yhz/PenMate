package com.penmate.backend.application.agent;

public record ToolExecutionResult(
        String pluginCode,
        String toolName,
        String output,
        boolean success,
        String errorMsg
) {
    public static ToolExecutionResult success(String pluginCode, String toolName, String output) {
        return new ToolExecutionResult(pluginCode, toolName, output, true, null);
    }

    public static ToolExecutionResult failed(String pluginCode, String toolName, String errorMsg) {
        return new ToolExecutionResult(pluginCode, toolName, "", false, errorMsg);
    }
}

