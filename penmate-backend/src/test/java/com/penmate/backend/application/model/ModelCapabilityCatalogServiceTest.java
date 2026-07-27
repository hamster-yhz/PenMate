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
}
