package com.penmate.backend.application.model.command;

import java.math.BigDecimal;

public final class ModelCommands {

    private ModelCommands() {
    }

    /**
     * CreateModelKeyCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record CreateModelKeyCommand(Long providerId,
                                        String keyName,
                                        String apiKey,
                                        Boolean isDefault,
                                        String status,
                                        Long operatorId) {
    }

    /**
     * UpdateModelKeyCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
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

    /**
     * CreatePolicyCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record CreatePolicyCommand(String policyName,
                                      String scene,
                                      Long providerModelId,
                                      String modelName,
                                      String baseUrl,
                                      Long userKeyId,
                                      Long officialKeyId,
                                      BigDecimal temperature,
                                      BigDecimal topP,
                                      Integer maxTokens,
                                      String fallbackPolicyJson,
                                      Boolean isDefault,
                                      Long operatorId) {
    }

    /**
     * UpdatePolicyCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record UpdatePolicyCommand(String policyName,
                                      String scene,
                                      Long providerModelId,
                                      String modelName,
                                      String baseUrl,
                                      Long userKeyId,
                                      Long officialKeyId,
                                      BigDecimal temperature,
                                      BigDecimal topP,
                                      Integer maxTokens,
                                      String fallbackPolicyJson,
                                      Boolean isDefault,
                                      Long operatorId) {
    }
}

