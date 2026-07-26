package com.penmate.backend.application.model.command;

public final class ModelCommands {

    private ModelCommands() {
    }

    public record CreateConfigurationCommand(
            Long providerId,
            String displayName,
            String modelType,
            String modelName,
            String baseUrl,
            String distanceMetric,
            Integer embeddingDimensions,
            String apiKey,
            Integer contextWindowTurns,
            Integer maxContextTokens
    ) {
        public CreateConfigurationCommand(Long providerId, String displayName, String modelType,
                                          String modelName, String baseUrl, String distanceMetric,
                                          String apiKey, Integer contextWindowTurns, Integer maxContextTokens) {
            this(providerId, displayName, modelType, modelName, baseUrl, distanceMetric, null,
                    apiKey, contextWindowTurns, maxContextTokens);
        }
    }

    public record UpdateConfigurationCommand(
            Long providerId,
            String displayName,
            String modelName,
            String baseUrl,
            String distanceMetric,
            Integer embeddingDimensions,
            boolean embeddingDimensionsSet,
            String apiKey,
            Integer contextWindowTurns,
            Integer maxContextTokens,
            String status
    ) {
        public UpdateConfigurationCommand(Long providerId, String displayName, String modelName,
                                          String baseUrl, String distanceMetric, String apiKey,
                                          Integer contextWindowTurns, Integer maxContextTokens, String status) {
            this(providerId, displayName, modelName, baseUrl, distanceMetric, null, false, apiKey,
                    contextWindowTurns, maxContextTokens, status);
        }
    }

    public record SaveUserModelPreferencesCommand(
            Long defaultCreativeModelConfigId,
            Long defaultContextSelectorModelConfigId,
            Long defaultEmbeddingModelConfigId,
            String defaultStoryBibleRoutingMode,
            Integer defaultChunkTargetCharacters,
            Integer defaultChunkOverlapCharacters,
            Integer defaultChunkMaxCharacters
    ) {
    }

    public record ProbeEmbeddingDimensionCommand(
            Long modelConfigId,
            Long providerId,
            String modelName,
            String baseUrl,
            Integer embeddingDimensions,
            String apiKey
    ) {
    }

    public record DiscoverModelsCommand(
            Long modelConfigId,
            Long providerId,
            String modelType,
            String baseUrl,
            String apiKey
    ) {
    }
}
