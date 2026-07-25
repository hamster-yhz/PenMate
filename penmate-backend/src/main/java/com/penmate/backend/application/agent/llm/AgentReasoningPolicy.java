package com.penmate.backend.application.agent.llm;

public record AgentReasoningPolicy(String effort, String summary) {

    public static final AgentReasoningPolicy AUTO = new AgentReasoningPolicy("medium", "auto");
    public static final AgentReasoningPolicy DISABLED = new AgentReasoningPolicy("none", "none");

    public AgentReasoningPolicy {
        effort = normalize(effort, "medium");
        summary = normalize(summary, "auto");
    }

    public boolean requestsReasoning() {
        return !"none".equalsIgnoreCase(effort) || requestsSummary();
    }

    public boolean requestsSummary() {
        return !"none".equalsIgnoreCase(summary);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toLowerCase();
    }
}
