package com.penmate.backend.infrastructure.llm.langchain4j;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.infrastructure.llm.langchain4j.provider.ProviderChatClient;
import com.penmate.backend.infrastructure.llm.langchain4j.provider.ProviderChatClientFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LangChain4jAgentLlmGatewayTest {

    @Test
    void UT_INFRA_LLM_LANGCHAIN4J_AGENT_LLM_GATEWAY_DELEGATES_GENERATE_TURN_TO_PROVIDER_AND_RETURNS_STRUCTURED_RESPONSE() {
        ProviderChatClientFactory factory = mock(ProviderChatClientFactory.class);
        ProviderChatClient providerChatClient = mock(ProviderChatClient.class);
        LangChain4jAgentLlmGateway gateway = new LangChain4jAgentLlmGateway(factory);
        AgentLlmExecutionConfig executionConfig = new AgentLlmExecutionConfig(
                1L,
                "openai-compatible",
                "https://example.com/v1",
                "sk-test",
                "gpt-test",
                "USER_KEY"
        );
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(Map.of("role", "user", "content", "hello")),
                List.of(),
                "auto"
        );
        AgentLlmTurnResponse expected = new AgentLlmTurnResponse(
                "tool_calls",
                "",
                List.of(new AgentLlmToolCall("call_1", "context_enhancer", "{\"prompt\":\"hello\"}")),
                "{}"
        );

        when(factory.get("openai-compatible")).thenReturn(providerChatClient);
        when(providerChatClient.generateTurn(same(request), same(executionConfig))).thenReturn(expected);

        AgentLlmTurnResponse actual = gateway.generateTurn(request, executionConfig);

        assertThat(actual).isEqualTo(expected);
        verify(factory).get("openai-compatible");
        verify(providerChatClient).generateTurn(same(request), same(executionConfig));
    }
}
