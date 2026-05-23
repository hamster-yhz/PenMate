package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

@Component
public class OpenAiProviderChatClient extends AbstractOpenAiCompatibleProviderChatClient {

    private static final Set<String> JSON_SCHEMA_UNSUPPORTED_HOSTS = Set.of("api.longcat.chat");

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
    protected boolean supportsJsonSchemaResponseFormat() {
        return true;
    }

    @Override
    protected boolean supportsJsonSchemaResponseFormat(String endpoint) {
        String host = extractHost(endpoint);
        if (host != null && JSON_SCHEMA_UNSUPPORTED_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            return false;
        }
        return supportsJsonSchemaResponseFormat();
    }

    private String extractHost(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        try {
            return URI.create(endpoint.trim()).getHost();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    public boolean supports(String providerCode) {
        return "openai".equalsIgnoreCase(providerCode);
    }
}

