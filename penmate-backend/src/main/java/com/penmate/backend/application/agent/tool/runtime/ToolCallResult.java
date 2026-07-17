package com.penmate.backend.application.agent.tool.runtime;

/**
 * tool 调用统一结果。
 * <p>该结果同时覆盖三类状态：执行成功、执行失败、等待审批。调用方需结合 {@code status} 决定是否继续 loop、挂起任务或终止流程。</p>
 */
public record ToolCallResult(
        String status,
        Long approvalId,
        String toolOutput,
        String errorCode,
        String errorMessage,
        java.util.Map<String, String> approvalPreview
) {
    private static final String DEFAULT_ERROR_CODE = "TOOL_CALL_FAILED";
    private static final String DEFAULT_ERROR_MESSAGE = "Unknown error";

    public ToolCallResult {
        status = normalizeStatus(status);
        approvalPreview = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(
                approvalPreview == null ? java.util.Map.of() : approvalPreview));
        if ("FAILED".equals(status)) {
            errorCode = normalizeText(errorCode, DEFAULT_ERROR_CODE);
            errorMessage = normalizeText(errorMessage, DEFAULT_ERROR_MESSAGE);
        }
    }

    public static ToolCallResult waitingApproval(Long approvalId) {
        return waitingApproval(approvalId, java.util.Map.of());
    }

    public static ToolCallResult waitingApproval(Long approvalId, java.util.Map<String, String> approvalPreview) {
        return new ToolCallResult("WAITING_APPROVAL", approvalId, null, null, null, approvalPreview);
    }

    public static ToolCallResult success(String toolOutput) {
        return new ToolCallResult("SUCCESS", null, toolOutput, null, null, java.util.Map.of());
    }

    public static ToolCallResult failed(String errorCode, String errorMessage) {
        return new ToolCallResult("FAILED", null, null, errorCode, errorMessage, java.util.Map.of());
    }

    public ToolCallResult(String status, Long approvalId, String toolOutput, String errorCode, String errorMessage) {
        this(status, approvalId, toolOutput, errorCode, errorMessage, java.util.Map.of());
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "FAILED";
        }
        return status.trim().toUpperCase();
    }

    private static String normalizeText(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
