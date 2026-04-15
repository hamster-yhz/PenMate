package com.penmate.backend.infrastructure.persistence.model;

import com.penmate.backend.domain.model.model.ModelProjectPolicy;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelProviderModel;
import com.penmate.backend.domain.model.model.ModelUserApiKey;
import com.penmate.backend.domain.model.repository.ModelRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * ModelRepositoryImpl。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Repository
public class ModelRepositoryImpl implements ModelRepository {

    private final ModelMapper modelMapper;

    public ModelRepositoryImpl(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    /**
     * 查询列表数据。
     *
     * @return 出参：处理结果
     */
    @Override
    public List<ModelProvider> listProviders() {
        return modelMapper.listProviders();
    }

    /**
     * 查询列表数据。
     *
     * @param providerCode 入参：providerCode
     * @return 出参：处理结果
     */
    @Override
    public List<ModelProviderModel> listProviderModels(String providerCode) {
        return modelMapper.listProviderModels(providerCode);
    }

    /**
     * 查询列表数据。
     *
     * @param userId 入参：userId
     * @return 出参：处理结果
     */
    @Override
    public List<ModelUserApiKey> listUserKeys(Long userId) {
        return modelMapper.listUserKeys(userId);
    }

    /**
     * 处理业务请求。
     *
     * @param userId 入参：userId
     * @param providerId 入参：providerId
     * @param keyName 入参：keyName
     * @param encryptedApiKey 入参：encryptedApiKey
     * @param maskedApiKey 入参：maskedApiKey
     * @param isDefault 入参：isDefault
     * @param status 入参：status
     * @return 出参：处理结果
     */
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

    /**
     * 处理业务请求。
     *
     * @param userId 入参：userId
     * @return 出参：处理结果
     */
    @Override
    public int clearDefaultUserKey(Long userId) {
        return modelMapper.clearDefaultUserKey(userId);
    }

    /**
     * 更新业务数据。
     *
     * @param userId 入参：userId
     * @param keyId 入参：keyId
     * @param keyName 入参：keyName
     * @param encryptedApiKey 入参：encryptedApiKey
     * @param maskedApiKey 入参：maskedApiKey
     * @param isDefault 入参：isDefault
     * @param status 入参：status
     * @return 出参：处理结果
     */
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

    /**
     * 处理业务请求。
     *
     * @param userId 入参：userId
     * @param keyId 入参：keyId
     * @return 出参：处理结果
     */
    @Override
    public int softDeleteUserKey(Long userId, Long keyId) {
        return modelMapper.softDeleteUserKey(userId, keyId);
    }

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    @Override
    public List<ModelProjectPolicy> listProjectPolicies(Long projectId) {
        return modelMapper.listProjectPolicies(projectId);
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param policyName 入参：policyName
     * @param scene 入参：scene
     * @param providerModelId 入参：providerModelId
     * @param userKeyId 入参：userKeyId
     * @param temperature 入参：temperature
     * @param topP 入参：topP
     * @param maxTokens 入参：maxTokens
     * @param fallbackPolicyJson 入参：fallbackPolicyJson
     * @param isDefault 入参：isDefault
     * @return 出参：处理结果
     */
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

    /**
     * 更新业务数据。
     *
     * @param projectId 入参：projectId
     * @param policyId 入参：policyId
     * @param policyName 入参：policyName
     * @param scene 入参：scene
     * @param providerModelId 入参：providerModelId
     * @param userKeyId 入参：userKeyId
     * @param temperature 入参：temperature
     * @param topP 入参：topP
     * @param maxTokens 入参：maxTokens
     * @param fallbackPolicyJson 入参：fallbackPolicyJson
     * @param isDefault 入参：isDefault
     * @return 出参：处理结果
     */
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

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param policyId 入参：policyId
     * @return 出参：处理结果
     */
    @Override
    public int softDeletePolicy(Long projectId, Long policyId) {
        return modelMapper.softDeletePolicy(projectId, policyId);
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    @Override
    public int clearDefaultPolicy(Long projectId) {
        return modelMapper.clearDefaultPolicy(projectId);
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param policyId 入参：policyId
     * @return 出参：处理结果
     */
    @Override
    public int setDefaultPolicy(Long projectId, Long policyId) {
        return modelMapper.setDefaultPolicy(projectId, policyId);
    }
}

