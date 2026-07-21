package com.penmate.backend.application.agent.llm;

import com.penmate.backend.domain.agent.model.AgentLlmMessage;

import java.time.Duration;
import java.util.List;

/**
 * LLM 单轮对话请求。
 */
public record AgentLlmTurnRequest(
        List<AgentLlmMessage> messages,
        List<AgentLlmToolSchema> tools,
        String toolChoice,
        Duration timeout
) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    public AgentLlmTurnRequest(List<AgentLlmMessage> messages,
                               List<AgentLlmToolSchema> tools,
                               String toolChoice) {
        this(messages, tools, toolChoice, DEFAULT_TIMEOUT);
    }

    public AgentLlmTurnRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        toolChoice = (toolChoice == null || toolChoice.isBlank()) ? "auto" : toolChoice.trim();
        timeout = timeout == null || timeout.isZero() || timeout.isNegative() ? DEFAULT_TIMEOUT : timeout;
    }
}
