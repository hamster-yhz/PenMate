package com.penmate.backend.application.model;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Bundled model capability catalog used when a provider does not expose a
 * trustworthy context limit through its model-list endpoint.
 */
@Service
public class ModelCapabilityCatalogService {
    public static final int FALLBACK_CONTEXT_TOKENS = 128_000;
    public static final int FALLBACK_OUTPUT_TOKENS = 8_192;
    private static final Instant CATALOG_DATE = Instant.parse("2026-07-27T00:00:00Z");
    private static final List<ModelCapability> CATALOG = List.of(
            exact("openai", "gpt-5.6-sol", 1_050_000, 128_000, "https://developers.openai.com/api/docs/models/gpt-5.6-sol"),
            exact("openai", "gpt-5.4", 1_050_000, 128_000, "https://developers.openai.com/api/docs/models/gpt-5.4"),
            exact("openai", "gpt-5.2", 400_000, 128_000, "https://developers.openai.com/api/docs/models/gpt-5.2"),
            exact("openai", "gpt-4.1", 1_047_576, 32_768, "https://developers.openai.com/api/docs/models/gpt-4.1"),
            exact("openai", "gpt-4o", 128_000, 16_384, "https://developers.openai.com/api/docs/models/gpt-4o"),
            exact("openai", "o3", 200_000, 100_000, "https://developers.openai.com/api/docs/models/o3"),
            exact("openai", "gpt-oss-120b", 131_072, 32_768, "https://developers.openai.com/api/docs/models/gpt-oss-120b"),
            prefix("claude", "claude-fable-5", 1_000_000, 128_000, "https://docs.anthropic.com/en/docs/about-claude/models/overview"),
            prefix("claude", "claude-opus-5", 1_000_000, 128_000, "https://docs.anthropic.com/en/docs/about-claude/models/overview"),
            prefix("claude", "claude-sonnet-5", 1_000_000, 128_000, "https://docs.anthropic.com/en/docs/about-claude/models/overview"),
            prefix("claude", "claude-opus-4-", 1_000_000, 128_000, "https://docs.anthropic.com/en/docs/about-claude/models/overview"),
            prefix("claude", "claude-sonnet-4-6", 1_000_000, 128_000, "https://docs.anthropic.com/en/docs/about-claude/models/overview"),
            prefix("claude", "claude-haiku-4-5", 200_000, 64_000, "https://docs.anthropic.com/en/docs/about-claude/models/overview"),
            prefix("claude", "claude-sonnet-4-5", 200_000, 64_000, "https://docs.anthropic.com/en/docs/about-claude/models/overview"),
            prefix("claude", "claude-opus-4-5", 200_000, 64_000, "https://docs.anthropic.com/en/docs/about-claude/models/overview"),
            prefix("gemini", "gemini-", 1_048_576, 65_536, "https://ai.google.dev/gemini-api/docs/models"),
            prefix("deepseek", "deepseek-v4", 1_000_000, 384_000, "https://api-docs.deepseek.com/quick_start/pricing"),
            prefix("deepseek", "deepseek-chat", 64_000, 8_192, "https://api-docs.deepseek.com/quick_start/pricing"),
            prefix("deepseek", "deepseek-reasoner", 64_000, 64_000, "https://api-docs.deepseek.com/quick_start/pricing"),
            prefix("xai", "grok-4.20", 1_000_000, 128_000, "https://docs.x.ai/developers/models"),
            prefix("xai", "grok-4.3", 1_000_000, 128_000, "https://docs.x.ai/developers/models"),
            prefix("xai", "grok-", 256_000, 128_000, "https://docs.x.ai/developers/models")
    );

    public Optional<ModelCapability> resolve(String providerCode, String modelName) {
        String provider = normalize(providerCode);
        String model = normalizeModel(modelName);
        if (provider.isBlank() || model.isBlank()) return Optional.empty();
        return CATALOG.stream()
                .filter(item -> item.matches(provider, model))
                .sorted(Comparator.comparingInt(ModelCapability::specificity).reversed())
                .findFirst();
    }

    public Resolution resolveForSave(String providerCode, String modelName,
                                     Integer requestedContext, Integer requestedOutput) {
        if (requestedContext != null && requestedContext > 0) {
            int output = requestedOutput == null || requestedOutput <= 0
                    ? FALLBACK_OUTPUT_TOKENS : requestedOutput;
            return new Resolution(requestedContext, output, "MANUAL", null, null);
        }
        Optional<ModelCapability> match = resolve(providerCode, modelName);
        if (match.isPresent()) {
            ModelCapability capability = match.get();
            int output = requestedOutput == null || requestedOutput <= 0
                    ? capability.maxOutputTokens() : requestedOutput;
            return new Resolution(capability.maxContextTokens(), output, "CATALOG",
                    capability.sourceUrl(), capability.observedAt());
        }
        return new Resolution(FALLBACK_CONTEXT_TOKENS,
                requestedOutput == null || requestedOutput <= 0 ? FALLBACK_OUTPUT_TOKENS : requestedOutput,
                "FALLBACK", null, CATALOG_DATE);
    }

    private static ModelCapability exact(String provider, String model, int context, int output, String source) {
        return new ModelCapability(provider, model, context, output, source, CATALOG_DATE, true);
    }

    private static ModelCapability prefix(String provider, String model, int context, int output, String source) {
        return new ModelCapability(provider, model, context, output, source, CATALOG_DATE, false);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeModel(String value) {
        String model = normalize(value);
        if (model.startsWith("models/")) model = model.substring("models/".length());
        return model;
    }

    public record Resolution(int maxContextTokens, int maxOutputTokens, String source,
                             String sourceUrl, Instant verifiedAt) {
    }

    public record ModelCapability(String providerCode, String modelPattern,
                                  int maxContextTokens, int maxOutputTokens,
                                  String sourceUrl, Instant observedAt, boolean exact) {
        boolean matches(String provider, String model) {
            String providerKey = provider.replaceAll("[^a-z0-9]", "");
            String catalogProviderKey = providerCode.replaceAll("[^a-z0-9]", "");
            boolean providerMatches = providerKey.equals(catalogProviderKey)
                    || providerKey.startsWith(catalogProviderKey)
                    || (providerCode.equals("claude") && provider.contains("anthropic"))
                    || (providerCode.equals("gemini") && provider.contains("google"));
            boolean modelMatches = exact ? model.equals(modelPattern) : model.startsWith(modelPattern);
            return providerMatches && modelMatches;
        }

        int specificity() {
            return modelPattern.length() + (exact ? 1000 : 0);
        }
    }
}
