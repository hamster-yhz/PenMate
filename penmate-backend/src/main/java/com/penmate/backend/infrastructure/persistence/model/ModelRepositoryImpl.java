package com.penmate.backend.infrastructure.persistence.model;

import com.penmate.backend.domain.model.model.ModelProjectPolicy;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelProviderModel;
import com.penmate.backend.domain.model.model.ModelUserApiKey;
import com.penmate.backend.domain.model.repository.ModelRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public class ModelRepositoryImpl implements ModelRepository {

    private final ModelMapper modelMapper;

    public ModelRepositoryImpl(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    @Override
    public List<ModelProvider> listProviders() {
        return modelMapper.listProviders();
    }

    @Override
    public List<ModelProviderModel> listProviderModels(String providerCode) {
        return modelMapper.listProviderModels(providerCode);
    }

    @Override
    public List<ModelUserApiKey> listUserKeys(Long userId) {
        return modelMapper.listUserKeys(userId);
    }

    @Override
    public int insertUserKey(Long userId,
                             Long providerId,
                             String keyName,
                             String encryptedApiKey,
                             String maskedApiKey,
                             boolean isDefault,
                             String status) {
        return modelMapper.insertUserKey(userId, providerId, keyName, encryptedApiKey, maskedApiKey, isDefault, status);
    }

    @Override
    public int clearDefaultUserKey(Long userId) {
        return modelMapper.clearDefaultUserKey(userId);
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
    public int softDeleteUserKey(Long userId, Long keyId) {
        return modelMapper.softDeleteUserKey(userId, keyId);
    }

    @Override
    public List<ModelProjectPolicy> listProjectPolicies(Long projectId) {
        return modelMapper.listProjectPolicies(projectId);
    }

    @Override
    public int insertPolicy(Long projectId,
                            String policyName,
                            String scene,
                            Long providerModelId,
                            Long userKeyId,
                            BigDecimal temperature,
                            BigDecimal topP,
                            Integer maxTokens,
                            String fallbackPolicyJson,
                            boolean isDefault) {
        return modelMapper.insertPolicy(projectId, policyName, scene, providerModelId, userKeyId, temperature, topP, maxTokens, fallbackPolicyJson, isDefault);
    }

    @Override
    public int updatePolicy(Long projectId,
                            Long policyId,
                            String policyName,
                            String scene,
                            Long providerModelId,
                            Long userKeyId,
                            BigDecimal temperature,
                            BigDecimal topP,
                            Integer maxTokens,
                            String fallbackPolicyJson,
                            Boolean isDefault) {
        return modelMapper.updatePolicy(projectId, policyId, policyName, scene, providerModelId, userKeyId, temperature, topP, maxTokens, fallbackPolicyJson, isDefault);
    }

    @Override
    public int softDeletePolicy(Long projectId, Long policyId) {
        return modelMapper.softDeletePolicy(projectId, policyId);
    }

    @Override
    public int clearDefaultPolicy(Long projectId) {
        return modelMapper.clearDefaultPolicy(projectId);
    }

    @Override
    public int setDefaultPolicy(Long projectId, Long policyId) {
        return modelMapper.setDefaultPolicy(projectId, policyId);
    }
}

