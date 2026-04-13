package com.penmate.backend.application.model.command;

import java.math.BigDecimal;

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

    public record CreatePolicyCommand(String policyName,
                                      String scene,
                                      Long providerModelId,
                                      Long userKeyId,
                                      BigDecimal temperature,
                                      BigDecimal topP,
                                      Integer maxTokens,
                                      String fallbackPolicyJson,
                                      Boolean isDefault,
                                      Long operatorId) {
    }

    public record UpdatePolicyCommand(String policyName,
                                      String scene,
                                      Long providerModelId,
                                      Long userKeyId,
                                      BigDecimal temperature,
                                      BigDecimal topP,
                                      Integer maxTokens,
                                      String fallbackPolicyJson,
                                      Boolean isDefault,
                                      Long operatorId) {
    }
}

