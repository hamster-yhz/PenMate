package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import org.springframework.stereotype.Component;

@Component
public class OpenAiProviderChatClient extends AbstractOpenAiCompatibleProviderChatClient {

    @Override
    protected String resolveBaseUrl(String rawBaseUrl) {
        return ensureSuffixPath(super.resolveBaseUrl(rawBaseUrl), "/v1");
    }

    @Override
    public boolean supports(String providerCode) {
        return "openai".equalsIgnoreCase(providerCode);
    }
}

