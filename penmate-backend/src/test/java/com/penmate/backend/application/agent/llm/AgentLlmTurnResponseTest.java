package com.penmate.backend.application.agent.llm;

import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLlmTurnResponseTest {

    @Test
    void should_expose_zero_constant_and_sum_token_usage() {
        assertThat(LlmTokenUsage.ZERO).isEqualTo(new LlmTokenUsage(0, 0, 0));
        assertThat(new LlmTokenUsage(11, 7, 18).add(new LlmTokenUsage(5, 3, 8)))
                .isEqualTo(new LlmTokenUsage(16, 10, 26));
    }

    @Test
    void should_default_token_usage_to_zero_when_not_provided() {
        AgentLlmTurnResponse response = new AgentLlmTurnResponse(
                "tool_calls",
                null,
                List.of(new AgentLlmToolCall("call_1", "context_enhancer", "{}")),
                "{}"
        );

        assertThat(response.tokenUsage()).isEqualTo(LlmTokenUsage.ZERO);
    }

    @Test
    void should_keep_explicit_token_usage_when_provided() {
        LlmTokenUsage tokenUsage = new LlmTokenUsage(21, 13, 34);

        AgentLlmTurnResponse response = new AgentLlmTurnResponse(
                "stop",
                "done",
                List.of(),
                "{}",
                tokenUsage
        );

        assertThat(response.tokenUsage()).isEqualTo(tokenUsage);
    }
}
