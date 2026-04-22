package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import org.springframework.stereotype.Component;

@Component
public class XaiProviderChatClient extends NativeOpenAiStyleHttpProviderChatClient {

    @Override
    protected String resolveChatCompletionsEndpoint(String rawBaseUrl) {
        String baseUrl = trim(rawBaseUrl);
        if (baseUrl == null) {
            return null;
        }
        if (!baseUrl.endsWith("/v1") && !baseUrl.contains("/v1/")) {
            baseUrl = baseUrl + "/v1";
        }
        return super.resolveChatCompletionsEndpoint(baseUrl);
    }

    @Override
    public boolean supports(String providerCode) {
        return "xai".equalsIgnoreCase(providerCode);
    }
}

