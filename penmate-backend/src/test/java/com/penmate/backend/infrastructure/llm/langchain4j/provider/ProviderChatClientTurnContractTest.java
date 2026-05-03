package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderChatClientTurnContractTest {

    @Test
    void UT_INFRA_LLM_PROVIDER_CHAT_CLIENT_DEFAULT_GENERATE_TURN_FAILS_FAST_WHEN_PROVIDER_HAS_NO_TOOL_MODE_SUPPORT() {
        ProviderChatClient client = new UnsupportedTurnProviderChatClient();

        assertThatThrownBy(() -> client.generateTurn(
                new AgentLlmTurnRequest(List.of(Map.of("role", "user", "content", "hello")), List.of(), "auto"),
                new AgentLlmExecutionConfig(1L, "gemini", "https://example.com", "sk", "model", "USER_KEY")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("LLM provider does not support structured turn generation");
    }

    private static final class UnsupportedTurnProviderChatClient implements ProviderChatClient {

        @Override
        public boolean supports(String providerCode) {
            return true;
        }

        @Override
        public String generate(String prompt, AgentLlmExecutionConfig executionConfig) {
            return "plain";
        }
    }
}
