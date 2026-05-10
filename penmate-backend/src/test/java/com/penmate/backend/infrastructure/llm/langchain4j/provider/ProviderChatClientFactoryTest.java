package com.penmate.backend.infrastructure.llm.langchain4j.provider;

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
}
