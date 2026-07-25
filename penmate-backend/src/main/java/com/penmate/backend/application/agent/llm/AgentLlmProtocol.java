package com.penmate.backend.application.agent.llm;

import java.util.Locale;

public enum AgentLlmProtocol {
    OPENAI_RESPONSES,
    OPENAI_CHAT_COMPLETIONS,
    ANTHROPIC_MESSAGES,
    UNKNOWN;

    public static AgentLlmProtocol from(String value) {
        if (value == null || value.isBlank()) return UNKNOWN;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }
}
