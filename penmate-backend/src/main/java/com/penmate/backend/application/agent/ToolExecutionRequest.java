package com.penmate.backend.application.agent;

public record ToolExecutionRequest(
        Long projectId,
        Long taskId,
        String prompt,
        String traceId
) {
}

