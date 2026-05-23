package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderChatClientTurnContractTest {

    private static final AgentLlmTurnRequest TURN_REQUEST = new AgentLlmTurnRequest(
            List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("hello")),
            List.of(),
            "auto"
    );

    @Test
    void UT_INFRA_LLM_PROVIDER_CHAT_CLIENT_DEFAULT_GENERATE_TURN_FAILS_FAST_WHEN_PROVIDER_HAS_NO_TOOL_MODE_SUPPORT() {
        ProviderChatClient client = new UnsupportedTurnProviderChatClient();

        assertThatThrownBy(() -> client.generateTurn(
                TURN_REQUEST,
                new AgentLlmExecutionConfig(1L, "gemini", "https://example.com", "sk", "model", "USER_KEY", 6)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("LLM provider does not support structured turn generation");
    }

    @Test
    void UT_INFRA_LLM_OPENAI_PROVIDER_CHAT_CLIENT_GENERATE_TURN_VALIDATES_EXECUTION_CONFIG_INSTEAD_OF_THROWING_UNSUPPORTED() {
        ProviderChatClient client = new OpenAiProviderChatClient();

        assertThatThrownBy(() -> client.generateTurn(TURN_REQUEST, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("LLM execution config is required");
    }

    @Test
    void UT_INFRA_LLM_CLAUDE_PROVIDER_CHAT_CLIENT_GENERATE_TURN_VALIDATES_EXECUTION_CONFIG_INSTEAD_OF_THROWING_UNSUPPORTED() {
        ProviderChatClient client = new ClaudeProviderChatClient();

        assertThatThrownBy(() -> client.generateTurn(TURN_REQUEST, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("LLM execution config is required");
    }

    @Test
    void UT_INFRA_LLM_GEMINI_PROVIDER_CHAT_CLIENT_GENERATE_TURN_VALIDATES_EXECUTION_CONFIG_INSTEAD_OF_THROWING_UNSUPPORTED() {
        ProviderChatClient client = new GeminiProviderChatClient();

        assertThatThrownBy(() -> client.generateTurn(TURN_REQUEST, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("LLM execution config is required");
    }

    @Test
    void UT_INFRA_LLM_OPENAI_COMPATIBLE_PROVIDER_CHAT_CLIENT_GENERATE_TURN_VALIDATES_EXECUTION_CONFIG_INSTEAD_OF_THROWING_UNSUPPORTED() {
        ProviderChatClient client = new OpenAiCompatibleProviderChatClient();

        assertThatThrownBy(() -> client.generateTurn(TURN_REQUEST, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("LLM execution config is required");
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
