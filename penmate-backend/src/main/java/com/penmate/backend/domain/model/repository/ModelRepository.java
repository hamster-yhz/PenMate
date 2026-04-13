package com.penmate.backend.domain.model.repository;

import com.penmate.backend.domain.model.model.ModelProjectPolicy;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelProviderModel;
import com.penmate.backend.domain.model.model.ModelUserApiKey;

import java.math.BigDecimal;
import java.util.List;

public interface ModelRepository {

    List<ModelProvider> listProviders();

    List<ModelProviderModel> listProviderModels(String providerCode);

    List<ModelUserApiKey> listUserKeys(Long userId);

    int insertUserKey(Long userId,
                      Long providerId,
                      String keyName,
                      String encryptedApiKey,
                      String maskedApiKey,
                      boolean isDefault,
                      String status);

    int clearDefaultUserKey(Long userId);

    int updateUserKey(Long userId,
                      Long keyId,
                      String keyName,
                      String encryptedApiKey,
                      String maskedApiKey,
                      Boolean isDefault,
                      String status);

    int softDeleteUserKey(Long userId, Long keyId);

    List<ModelProjectPolicy> listProjectPolicies(Long projectId);

    int insertPolicy(Long projectId,
                     String policyName,
                     String scene,
                     Long providerModelId,
                     Long userKeyId,
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
                     Long userKeyId,
                     BigDecimal temperature,
                     BigDecimal topP,
                     Integer maxTokens,
                     String fallbackPolicyJson,
                     Boolean isDefault);

    int softDeletePolicy(Long projectId, Long policyId);

    int clearDefaultPolicy(Long projectId);

    int setDefaultPolicy(Long projectId, Long policyId);
}

