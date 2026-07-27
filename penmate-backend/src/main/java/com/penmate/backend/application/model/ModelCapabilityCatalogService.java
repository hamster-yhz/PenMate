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
    private static final String OPENAI_REASONING_SOURCE = "https://developers.openai.com/api/docs/guides/reasoning";
    private static final String ANTHROPIC_EFFORT_SOURCE = "https://docs.anthropic.com/en/docs/build-with-claude/effort";
    private static final String GEMINI_THINKING_SOURCE = "https://ai.google.dev/gemini-api/docs/openai";
    private static final List<ReasoningRule> REASONING_CATALOG = List.of(
            reasoningExact("openai", "gpt-5.6-sol",
                    values("AUTO", "NONE", "LOW", "MEDIUM", "HIGH", "XHIGH", "MAX"),
                    values("AUTO", "STANDARD", "PRO"), summaries(), OPENAI_REASONING_SOURCE),
            reasoningExact("openai", "gpt-5.4",
                    values("AUTO", "NONE", "LOW", "MEDIUM", "HIGH", "XHIGH"),
                    values("AUTO", "STANDARD"), summaries(), OPENAI_REASONING_SOURCE),
            reasoningExact("openai", "gpt-5.2",
                    values("AUTO", "NONE", "LOW", "MEDIUM", "HIGH", "XHIGH"),
                    values("AUTO", "STANDARD"), summaries(), OPENAI_REASONING_SOURCE),
            reasoningExact("openai", "o3", values("AUTO", "LOW", "MEDIUM", "HIGH"),
                    values("AUTO"), summaries(), OPENAI_REASONING_SOURCE),
            reasoningExact("openai", "gpt-oss-120b", values("AUTO", "LOW", "MEDIUM", "HIGH"),
                    values("AUTO"), summaries(), OPENAI_REASONING_SOURCE),
            reasoningExact("openai", "gpt-4.1", values("AUTO"), values("AUTO"), values("AUTO"),
                    OPENAI_REASONING_SOURCE),
            reasoningExact("openai", "gpt-4o", values("AUTO"), values("AUTO"), values("AUTO"),
                    OPENAI_REASONING_SOURCE),

            reasoningPrefix("claude", "claude-fable-5", anthropicExtendedEfforts(), anthropicModes(),
                    values("AUTO"), ANTHROPIC_EFFORT_SOURCE),
            reasoningPrefix("claude", "claude-mythos-5", anthropicExtendedEfforts(), anthropicModes(),
                    values("AUTO"), ANTHROPIC_EFFORT_SOURCE),
            reasoningPrefix("claude", "claude-opus-5", anthropicExtendedEfforts(), anthropicModes(),
                    values("AUTO"), ANTHROPIC_EFFORT_SOURCE),
            reasoningPrefix("claude", "claude-sonnet-5", anthropicExtendedEfforts(), anthropicModes(),
                    values("AUTO"), ANTHROPIC_EFFORT_SOURCE),
            reasoningPrefix("claude", "claude-opus-4-8", anthropicExtendedEfforts(), anthropicModes(),
                    values("AUTO"), ANTHROPIC_EFFORT_SOURCE),
            reasoningPrefix("claude", "claude-opus-4-7", anthropicExtendedEfforts(), anthropicModes(),
                    values("AUTO"), ANTHROPIC_EFFORT_SOURCE),
            reasoningPrefix("claude", "claude-opus-4-6", anthropicStandardEfforts(), anthropicModes(),
                    values("AUTO"), ANTHROPIC_EFFORT_SOURCE),
            reasoningPrefix("claude", "claude-sonnet-4-6", anthropicStandardEfforts(), anthropicModes(),
                    values("AUTO"), ANTHROPIC_EFFORT_SOURCE),
            reasoningPrefix("claude", "claude-opus-4-5", values("AUTO", "LOW", "MEDIUM", "HIGH"),
                    values("AUTO"), values("AUTO"), ANTHROPIC_EFFORT_SOURCE),

            reasoningPrefix("gemini", "gemini-2.5-flash",
                    values("AUTO", "NONE", "MINIMAL", "LOW", "MEDIUM", "HIGH"), values("AUTO"),
                    values("AUTO"), GEMINI_THINKING_SOURCE),
            reasoningPrefix("gemini", "gemini-2.5-pro",
                    values("AUTO", "MINIMAL", "LOW", "MEDIUM", "HIGH"), values("AUTO"),
                    values("AUTO"), GEMINI_THINKING_SOURCE),
            reasoningPrefix("gemini", "gemini-3",
                    values("AUTO", "MINIMAL", "LOW", "MEDIUM", "HIGH"), values("AUTO"),
                    values("AUTO"), GEMINI_THINKING_SOURCE)
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

    public ReasoningCapabilities resolveReasoning(String providerCode, String modelName, String protocolCode) {
        String provider = normalize(providerCode);
        String model = normalizeModel(modelName);
        Optional<ReasoningRule> match = REASONING_CATALOG.stream()
                .filter(item -> item.matches(provider, model))
                .sorted(Comparator.comparingInt(ReasoningRule::specificity).reversed())
                .findFirst();
        if (match.isPresent()) {
            ReasoningRule rule = match.get();
            return new ReasoningCapabilities(rule.efforts(), rule.modes(), rule.summaries(),
                    "CATALOG", rule.sourceUrl(), CATALOG_DATE);
        }
        return protocolReasoningCapabilities(provider, protocolCode);
    }

    public static ReasoningCapabilities protocolReasoningCapabilities(String providerCode, String protocolCode) {
        String provider = providerCode == null ? "" : providerCode.trim().toLowerCase(Locale.ROOT);
        String protocol = protocolCode == null ? "" : protocolCode.trim().toUpperCase(Locale.ROOT);
        if ("ANTHROPIC_MESSAGES".equals(protocol)) {
            return new ReasoningCapabilities(anthropicExtendedEfforts(), anthropicModes(), values("AUTO"),
                    "PROTOCOL", ANTHROPIC_EFFORT_SOURCE, CATALOG_DATE);
        }
        if ("OPENAI_RESPONSES".equals(protocol)) {
            return new ReasoningCapabilities(
                    values("AUTO", "NONE", "MINIMAL", "LOW", "MEDIUM", "HIGH", "XHIGH", "MAX"),
                    values("AUTO", "STANDARD", "PRO"), summaries(),
                    "PROTOCOL", OPENAI_REASONING_SOURCE, CATALOG_DATE);
        }
        if ("OPENAI_CHAT_COMPLETIONS".equals(protocol)) {
            List<String> efforts = provider.contains("gemini") || provider.contains("google")
                    ? values("AUTO", "MINIMAL", "LOW", "MEDIUM", "HIGH")
                    : values("AUTO", "NONE", "MINIMAL", "LOW", "MEDIUM", "HIGH", "XHIGH", "MAX");
            return new ReasoningCapabilities(efforts, values("AUTO"), values("AUTO"),
                    "PROTOCOL", provider.contains("gemini") || provider.contains("google")
                        ? GEMINI_THINKING_SOURCE : OPENAI_REASONING_SOURCE, CATALOG_DATE);
        }
        return new ReasoningCapabilities(values("AUTO"), values("AUTO"), values("AUTO"),
                "UNSUPPORTED", null, CATALOG_DATE);
    }

    private static ModelCapability exact(String provider, String model, int context, int output, String source) {
        return new ModelCapability(provider, model, context, output, source, CATALOG_DATE, true);
    }

    private static ModelCapability prefix(String provider, String model, int context, int output, String source) {
        return new ModelCapability(provider, model, context, output, source, CATALOG_DATE, false);
    }

    private static ReasoningRule reasoningExact(String provider, String model, List<String> efforts,
                                                 List<String> modes, List<String> summaries, String source) {
        return new ReasoningRule(provider, model, efforts, modes, summaries, source, true);
    }

    private static ReasoningRule reasoningPrefix(String provider, String model, List<String> efforts,
                                                  List<String> modes, List<String> summaries, String source) {
        return new ReasoningRule(provider, model, efforts, modes, summaries, source, false);
    }

    private static List<String> values(String... values) {
        return List.of(values);
    }

    private static List<String> summaries() {
        return values("AUTO", "NONE", "CONCISE", "DETAILED");
    }

    private static List<String> anthropicStandardEfforts() {
        return values("AUTO", "LOW", "MEDIUM", "HIGH", "MAX");
    }

    private static List<String> anthropicExtendedEfforts() {
        return values("AUTO", "LOW", "MEDIUM", "HIGH", "XHIGH", "MAX");
    }

    private static List<String> anthropicModes() {
        return values("AUTO", "ADAPTIVE", "DISABLED");
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

    public record ReasoningCapabilities(List<String> efforts, List<String> modes, List<String> summaries,
                                        String source, String sourceUrl, Instant verifiedAt) {
        public ReasoningCapabilities {
            efforts = List.copyOf(efforts);
            modes = List.copyOf(modes);
            summaries = List.copyOf(summaries);
        }
    }

    private record ReasoningRule(String providerCode, String modelPattern, List<String> efforts,
                                 List<String> modes, List<String> summaries, String sourceUrl, boolean exact) {
        boolean matches(String provider, String model) {
            String providerKey = provider.replaceAll("[^a-z0-9]", "");
            String catalogProviderKey = providerCode.replaceAll("[^a-z0-9]", "");
            boolean providerMatches = providerKey.equals(catalogProviderKey)
                    || providerKey.startsWith(catalogProviderKey)
                    || (providerCode.equals("claude") && provider.contains("anthropic"))
                    || (providerCode.equals("gemini") && provider.contains("google"));
            return providerMatches && (exact ? model.equals(modelPattern) : model.startsWith(modelPattern));
        }

        int specificity() {
            return modelPattern.length() + (exact ? 1000 : 0);
        }
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
