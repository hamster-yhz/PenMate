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
            String apiKey,
            Integer contextWindowTurns,
            Integer maxContextTokens
    ) {
    }

    public record UpdateConfigurationCommand(
            Long providerId,
            String displayName,
            String modelName,
            String baseUrl,
            String distanceMetric,
            String apiKey,
            Integer contextWindowTurns,
            Integer maxContextTokens,
            String status
    ) {
    }

    public record SaveUserModelPreferencesCommand(
            Long defaultMainChatModelConfigId,
            Long defaultWorkerChatModelConfigId,
            Long defaultEmbeddingModelConfigId,
            Long defaultRouterModelConfigId,
            String defaultStoryBibleRoutingMode,
            Integer defaultChunkTargetCharacters,
            Integer defaultChunkOverlapCharacters,
            Integer defaultChunkMaxCharacters
    ) {
    }
}
