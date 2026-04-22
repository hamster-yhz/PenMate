package com.penmate.backend.application.model;

import com.penmate.backend.domain.model.model.ModelProvider;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 代码内置模型供应商目录。
 */
public final class BuiltinModelProviders {

    private static final List<ModelProvider> PROVIDERS = List.of(
            provider(1L, "openai", "OpenAI", "https://api.openai.com/v1"),
            provider(2L, "xai", "xAI", "https://api.x.ai/v1"),
            provider(3L, "longcat", "Longcat", null),
            provider(4L, "claude", "Claude", "https://api.anthropic.com"),
            provider(5L, "gemini", "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai"),
            provider(6L, "deepseek", "DeepSeek", "https://api.deepseek.com/v1")
    );

    private BuiltinModelProviders() {
    }

    public static List<ModelProvider> list() {
        return PROVIDERS;
    }

    public static Optional<ModelProvider> findById(Long providerId) {
        if (providerId == null) {
            return Optional.empty();
        }
        return PROVIDERS.stream()
                .filter(item -> providerId.equals(item.getId()))
                .findFirst();
    }

    public static Optional<ModelProvider> findByCode(String providerCode) {
        if (providerCode == null || providerCode.isBlank()) {
            return Optional.empty();
        }
        String normalized = providerCode.trim().toLowerCase(Locale.ROOT);
        return PROVIDERS.stream()
                .filter(item -> normalized.equals(item.getCode()))
                .findFirst();
    }

    private static ModelProvider provider(Long id, String code, String name, String baseUrl) {
        ModelProvider provider = new ModelProvider();
        provider.setId(id);
        provider.setCode(code);
        provider.setName(name);
        provider.setBaseUrl(baseUrl);
        provider.setAuthType("bearer");
        provider.setStatus("active");
        return provider;
    }
}
