package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import org.springframework.stereotype.Component;

@Component
public class OpenAiCompatibleProviderChatClient extends AbstractOpenAiCompatibleProviderChatClient {

    @Override
    public boolean supports(String providerCode) {
        return "openai-compatible".equalsIgnoreCase(providerCode);
    }
}
