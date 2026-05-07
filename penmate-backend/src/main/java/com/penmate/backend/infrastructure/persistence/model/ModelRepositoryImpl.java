package com.penmate.backend.infrastructure.persistence.model;

import com.penmate.backend.domain.model.model.ModelOfficialApiKey;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelUserApiKey;
import com.penmate.backend.domain.model.repository.ModelRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 模型配置仓储实现。
 */
@Repository
public class ModelRepositoryImpl implements ModelRepository {

    private final ModelMapper modelMapper;

    public ModelRepositoryImpl(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    @Override
    public List<ModelUserApiKey> listUserKeys(Long userId) {
        return modelMapper.listUserKeys(userId);
    }

    @Override
    public List<ModelOfficialApiKey> listOfficialKeys() {
        return modelMapper.listOfficialKeys();
    }

    @Override
    public int insertUserKey(Long userApiKeyId,
                             Long userId,
                             Long providerId,
                             String keyName,
                             String encryptedApiKey,
                             String maskedApiKey,
                             boolean isDefault,
                             String status) {
        return modelMapper.insertUserKey(userApiKeyId, userId, providerId, keyName, encryptedApiKey, maskedApiKey, isDefault, status);
    }

    @Override
    public int insertOfficialKey(Long officialApiKeyId,
                                 Long providerId,
                                 String keyName,
                                 String encryptedApiKey,
                                 String maskedApiKey,
                                 boolean isDefault,
                                 String status) {
        return modelMapper.insertOfficialKey(officialApiKeyId, providerId, keyName, encryptedApiKey, maskedApiKey, isDefault, status);
    }

    @Override
    public int clearDefaultUserKey(Long userId) {
        return modelMapper.clearDefaultUserKey(userId);
    }

    @Override
    public int clearDefaultOfficialKey(Long providerId) {
        return modelMapper.clearDefaultOfficialKey(providerId);
    }

    @Override
    public int updateUserKey(Long userId,
                             Long keyId,
                             String keyName,
                             String encryptedApiKey,
                             String maskedApiKey,
                             Boolean isDefault,
                             String status) {
        return modelMapper.updateUserKey(userId, keyId, keyName, encryptedApiKey, maskedApiKey, isDefault, status);
    }

    @Override
    public int updateOfficialKey(Long keyId,
                                 String keyName,
                                 String encryptedApiKey,
                                 String maskedApiKey,
                                 Boolean isDefault,
                                 String status) {
        return modelMapper.updateOfficialKey(keyId, keyName, encryptedApiKey, maskedApiKey, isDefault, status);
    }

    @Override
    public int softDeleteUserKey(Long userId, Long keyId) {
        return modelMapper.softDeleteUserKey(userId, keyId);
    }

    @Override
    public int softDeleteOfficialKey(Long keyId) {
        return modelMapper.softDeleteOfficialKey(keyId);
    }

    @Override
    public ModelProvider findProvider(Long providerId) {
        return modelMapper.findProvider(providerId);
    }

    @Override
    public ModelUserApiKey findUserKey(Long userKeyId) {
        return modelMapper.findUserKey(userKeyId);
    }

    @Override
    public ModelOfficialApiKey findOfficialKey(Long officialKeyId) {
        return modelMapper.findOfficialKey(officialKeyId);
    }

    @Override
    public ModelOfficialApiKey findDefaultOfficialKey(Long providerId) {
        return modelMapper.findDefaultOfficialKey(providerId);
    }

    @Override
    public List<Map<String, Object>> listUserModelConfigs(Long userId) {
        return modelMapper.listUserModelConfigs(userId);
    }

    @Override
    public Map<String, Object> findUserModelConfig(Long userId, Long modelConfigId) {
        return modelMapper.findUserModelConfig(userId, modelConfigId);
    }

    @Override
    public int insertUserModelConfig(Long modelConfigId,
                                     Long userId,
                                     Long providerId,
                                     String modelName,
                                     String baseUrl,
                                     String keySourceType,
                                     Long userKeyId,
                                     Long officialKeyId,
                                     String status) {
        return modelMapper.insertUserModelConfig(modelConfigId, userId, providerId, modelName, baseUrl, keySourceType, userKeyId, officialKeyId, status);
    }

    @Override
    public int updateUserModelConfig(Long userId,
                                     Long modelConfigId,
                                     Long providerId,
                                     String modelName,
                                     String baseUrl,
                                     String keySourceType,
                                     Long userKeyId,
                                     Long officialKeyId,
                                     String status) {
        return modelMapper.updateUserModelConfig(userId, modelConfigId, providerId, modelName, baseUrl, keySourceType, userKeyId, officialKeyId, status);
    }

    @Override
    public int softDeleteUserModelConfig(Long userId, Long modelConfigId) {
        return modelMapper.softDeleteUserModelConfig(userId, modelConfigId);
    }

    @Override
    public int updateUserModelPreferences(Long userId, Long mainAgentModelConfigId, Long dirtyWorkAgentModelConfigId) {
        return modelMapper.updateUserModelPreferences(userId, mainAgentModelConfigId, dirtyWorkAgentModelConfigId);
    }

    @Override
    public boolean existsUsableModelConfig(Long userId, Long modelConfigId) {
        return modelMapper.countUsableModelConfig(userId, modelConfigId) > 0;
    }
}
