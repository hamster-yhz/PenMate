package com.penmate.backend.application.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelCapabilityCatalogServiceTest {

    private final ModelCapabilityCatalogService service = new ModelCapabilityCatalogService();

    @Test
    void resolvesExactCurrentOpenAiModelBeforeFallbacks() {
        ModelCapabilityCatalogService.Resolution result = service.resolveForSave(
                "openai-compatible", "gpt-5.6-sol", null, null);

        assertThat(result.maxContextTokens()).isEqualTo(1_050_000);
        assertThat(result.maxOutputTokens()).isEqualTo(128_000);
        assertThat(result.source()).isEqualTo("CATALOG");
        assertThat(result.sourceUrl()).contains("gpt-5.6-sol");
    }

    @Test
    void usesTheMostSpecificPrefixAndNormalizesGeminiModelNames() {
        ModelCapabilityCatalogService.Resolution claude = service.resolveForSave(
                "anthropic", "claude-sonnet-4-5-20250929", null, null);
        ModelCapabilityCatalogService.Resolution gemini = service.resolveForSave(
                "google-gemini", "models/gemini-3.1-pro", null, null);

        assertThat(claude.maxContextTokens()).isEqualTo(200_000);
        assertThat(gemini.maxContextTokens()).isEqualTo(1_048_576);
    }

    @Test
    void explicitCapacityAlwaysWinsAndUnknownModelsUseConservativeFallback() {
        ModelCapabilityCatalogService.Resolution manual = service.resolveForSave(
                "openai", "gpt-5.6-sol", 777_000, 12_345);
        ModelCapabilityCatalogService.Resolution unknown = service.resolveForSave(
                "custom", "private-model", null, null);

        assertThat(manual.source()).isEqualTo("MANUAL");
        assertThat(manual.maxContextTokens()).isEqualTo(777_000);
        assertThat(manual.maxOutputTokens()).isEqualTo(12_345);
        assertThat(unknown.source()).isEqualTo("FALLBACK");
        assertThat(unknown.maxContextTokens()).isEqualTo(128_000);
    }

    @Test
    void resolves_model_specific_reasoning_controls_and_protocol_fallbacks() {
        var claude46 = service.resolveReasoning("anthropic", "claude-sonnet-4-6", "ANTHROPIC_MESSAGES");
        var claude5 = service.resolveReasoning("claude", "claude-sonnet-5-20260701", "ANTHROPIC_MESSAGES");
        var gemini3 = service.resolveReasoning("google-gemini", "models/gemini-3.1-pro", "OPENAI_CHAT_COMPLETIONS");
        var privateModel = service.resolveReasoning("openai-compatible", "private-reasoner", "OPENAI_CHAT_COMPLETIONS");

        assertThat(claude46.efforts()).containsExactly("AUTO", "LOW", "MEDIUM", "HIGH", "MAX");
        assertThat(claude46.modes()).containsExactly("AUTO", "ADAPTIVE", "DISABLED");
        assertThat(claude5.efforts()).contains("XHIGH", "MAX");
        assertThat(gemini3.efforts()).containsExactly("AUTO", "MINIMAL", "LOW", "MEDIUM", "HIGH");
        assertThat(gemini3.efforts()).doesNotContain("NONE");
        assertThat(privateModel.source()).isEqualTo("PROTOCOL");
    }
}
