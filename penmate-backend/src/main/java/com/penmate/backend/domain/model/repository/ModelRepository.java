package com.penmate.backend.domain.model.repository;

import com.penmate.backend.domain.model.model.ModelProjectPolicy;
import com.penmate.backend.domain.model.model.ModelOfficialApiKey;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelUserApiKey;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ModelRepository {

    List<ModelUserApiKey> listUserKeys(Long userId);

    List<ModelOfficialApiKey> listOfficialKeys();

    int insertUserKey(Long userApiKeyId,
                      Long userId,
                      Long providerId,
                      String keyName,
                      String encryptedApiKey,
                      String maskedApiKey,
                      boolean isDefault,
                      String status);

    int insertOfficialKey(Long officialApiKeyId,
                          Long providerId,
                          String keyName,
                          String encryptedApiKey,
                          String maskedApiKey,
                          boolean isDefault,
                          String status);

    int clearDefaultUserKey(Long userId);

    int clearDefaultOfficialKey(Long providerId);

    int updateUserKey(Long userId,
                      Long keyId,
                      String keyName,
                      String encryptedApiKey,
                      String maskedApiKey,
                      Boolean isDefault,
                      String status);

    int updateOfficialKey(Long keyId,
                          String keyName,
                          String encryptedApiKey,
                          String maskedApiKey,
                          Boolean isDefault,
                          String status);

    int softDeleteUserKey(Long userId, Long keyId);

    int softDeleteOfficialKey(Long keyId);

    List<ModelProjectPolicy> listProjectPolicies(Long projectId);

    ModelProjectPolicy findProjectPolicy(Long projectId, Long policyId);

    ModelProjectPolicy findDefaultProjectPolicy(Long projectId);

    ModelProvider findProvider(Long providerId);

    ModelUserApiKey findUserKey(Long userKeyId);

    ModelOfficialApiKey findOfficialKey(Long officialKeyId);

    ModelOfficialApiKey findDefaultOfficialKey(Long providerId);

    int insertPolicy(Long projectPolicyId,
                     Long projectId,
                     String policyName,
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
                     boolean isDefault);

    int updatePolicy(Long projectId,
                     Long policyId,
                     String policyName,
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
                     Boolean isDefault);

    int softDeletePolicy(Long projectId, Long policyId);

    int clearDefaultPolicy(Long projectId);

    int setDefaultPolicy(Long projectId, Long policyId);

    List<Map<String, Object>> listUserModelConfigs(Long userId);

    int updateUserModelPreferences(Long userId, Long mainAgentModelConfigId, Long dirtyWorkAgentModelConfigId);

    boolean existsUsableModelConfig(Long userId, Long modelConfigId);
}
