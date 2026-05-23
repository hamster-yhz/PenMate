package com.penmate.backend.domain.model.repository;

import com.penmate.backend.domain.model.model.ModelOfficialApiKey;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelUserApiKey;

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

    ModelProvider findProvider(Long providerId);

    ModelUserApiKey findUserKey(Long userKeyId);

    ModelOfficialApiKey findOfficialKey(Long officialKeyId);

    ModelOfficialApiKey findDefaultOfficialKey(Long providerId);

    List<Map<String, Object>> listUserModelConfigs(Long userId);

    Map<String, Object> findUserModelConfig(Long userId, Long modelConfigId);

    int insertUserModelConfig(Long modelConfigId,
                              Long userId,
                              Long providerId,
                              String modelName,
                              String baseUrl,
                              String keySourceType,
                              Long userKeyId,
                              Long officialKeyId,
                              Integer contextWindowTurns,
                              Integer maxContextTokens,
                              String status);

    int updateUserModelConfig(Long userId,
                              Long modelConfigId,
                              Long providerId,
                              String modelName,
                              String baseUrl,
                              String keySourceType,
                              Long userKeyId,
                              Long officialKeyId,
                              Integer contextWindowTurns,
                              Integer maxContextTokens,
                              String status);

    int softDeleteUserModelConfig(Long userId, Long modelConfigId);

    int updateUserModelPreferences(Long userId, Long mainAgentModelConfigId, Long dirtyWorkAgentModelConfigId);

    boolean existsUsableModelConfig(Long userId, Long modelConfigId);
}
