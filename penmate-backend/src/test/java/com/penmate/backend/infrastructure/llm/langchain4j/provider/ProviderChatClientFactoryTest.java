package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderChatClientFactoryTest {

    @Test
    void UT_INFRA_LLM_PROVIDER_CHAT_CLIENT_FACTORY_RETURNS_OPENAI_COMPATIBLE_CLIENT_FOR_OPENAI_COMPATIBLE_PROVIDER_CODE() {
        OpenAiCompatibleProviderChatClient openAiCompatibleProviderChatClient = new OpenAiCompatibleProviderChatClient();
        ProviderChatClientFactory factory = new ProviderChatClientFactory(List.of(
                new OpenAiProviderChatClient(),
                openAiCompatibleProviderChatClient,
                new GeminiProviderChatClient()
        ));

        ProviderChatClient actual = factory.get("openai-compatible");

        assertThat(actual).isSameAs(openAiCompatibleProviderChatClient);
    }

    @Test
    void selects_openai_adapter_by_protocol_instead_of_provider_name_alone() {
        OpenAiProviderChatClient chat = new OpenAiProviderChatClient();
        OpenAiResponsesProviderChatClient responses = new OpenAiResponsesProviderChatClient();
        ProviderChatClientFactory factory = new ProviderChatClientFactory(List.of(chat, responses));

        ProviderChatClient actual = factory.get(AgentLlmExecutionConfig.builder()
                .providerCode("openai")
                .protocolCode("OPENAI_RESPONSES")
                .build());

        assertThat(actual).isSameAs(responses);
    }
}
