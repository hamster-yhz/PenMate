package com.penmate.backend.application.model.command;

public final class ModelCommands {

    private ModelCommands() {
    }

    public record CreateModelKeyCommand(Long providerId,
                                        String keyName,
                                        String apiKey,
                                        Boolean isDefault,
                                        String status,
                                        Long operatorId) {
    }

    public record UpdateModelKeyCommand(String keyName,
                                        String apiKey,
                                        Boolean isDefault,
                                        String status,
                                        Long operatorId) {
    }

    public record CreateOfficialModelKeyCommand(Long providerId,
                                                String keyName,
                                                String apiKey,
                                                Boolean isDefault,
                                                String status,
                                                Long operatorId) {
    }

    public record UpdateOfficialModelKeyCommand(String keyName,
                                                String apiKey,
                                                Boolean isDefault,
                                                String status,
                                                Long operatorId) {
    }

    public record CreateUserModelConfigCommand(Long providerId,
                                               String modelName,
                                               String baseUrl,
                                               String keySourceType,
                                               String apiKey,
                                               Integer contextWindowTurns,
                                               Integer maxContextTokens,
                                               String status,
                                               Long operatorId) {
    }

    public record UpdateUserModelConfigCommand(Long providerId,
                                               String modelName,
                                               String baseUrl,
                                               String keySourceType,
                                               String apiKey,
                                               Integer contextWindowTurns,
                                               Integer maxContextTokens,
                                               String status,
                                               Long operatorId) {
    }

    public record SaveUserModelPreferencesCommand(Long mainAgentModelConfigId,
                                                  Long dirtyWorkAgentModelConfigId) {
    }
}
