package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import org.springframework.stereotype.Component;

@Component
public class OpenAiProviderChatClient extends AbstractOpenAiCompatibleProviderChatClient {

    @Override
    protected String resolveBaseUrl(String rawBaseUrl) {
        String baseUrl = super.resolveBaseUrl(rawBaseUrl);
        if (baseUrl == null) {
            return null;
        }
        String normalized = baseUrl.trim();
        if (normalized.endsWith("/chat/completions") || normalized.endsWith("/v1") || normalized.contains("/v1/")) {
            return normalized;
        }
        return ensureSuffixPath(normalized, "/v1");
    }

    @Override
    public boolean supports(String providerCode) {
        return "openai".equalsIgnoreCase(providerCode);
    }
}

