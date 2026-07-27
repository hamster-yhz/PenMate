package com.penmate.backend.application.agent.llm;

public record AgentReasoningPolicy(String effort, String summary, String mode) {

    public static final AgentReasoningPolicy AUTO = new AgentReasoningPolicy("auto", "auto", "auto");
    public static final AgentReasoningPolicy DISABLED = new AgentReasoningPolicy("none", "none", "disabled");

    public AgentReasoningPolicy(String effort, String summary) {
        this(effort, summary, "auto");
    }

    public AgentReasoningPolicy {
        effort = normalize(effort, "auto");
        summary = normalize(summary, "auto");
        mode = normalize(mode, "auto");
    }

    public boolean requestsReasoning() {
        return disabled() || explicitEffort() || explicitMode() || requestsSummary();
    }

    public boolean requestsSummary() {
        return !disabled() && !"none".equalsIgnoreCase(summary);
    }

    public boolean explicitEffort() {
        return !"auto".equalsIgnoreCase(effort);
    }

    public boolean explicitMode() {
        return !"auto".equalsIgnoreCase(mode) && !"disabled".equalsIgnoreCase(mode);
    }

    public boolean explicitSummary() {
        return !"auto".equalsIgnoreCase(summary);
    }

    public boolean disabled() {
        return "none".equalsIgnoreCase(effort) || "disabled".equalsIgnoreCase(mode);
    }

    public boolean allowsCompatibilityFallback() {
        return !explicitEffort() && !explicitMode() && !explicitSummary();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toLowerCase();
    }
}
