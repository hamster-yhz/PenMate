package com.penmate.backend.infrastructure.persistence.model;

import com.penmate.backend.domain.model.model.ModelProjectPolicy;
import com.penmate.backend.domain.model.model.ModelOfficialApiKey;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelUserApiKey;
import com.penmate.backend.domain.model.repository.ModelRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 模型配置仓储实现。
 * <p>负责将模型提供商、用户密钥与项目策略相关的持久化操作委托给 {@link ModelMapper}。</p>
 */
@Repository
public class ModelRepositoryImpl implements ModelRepository {

    private final ModelMapper modelMapper;

    public ModelRepositoryImpl(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    /**
     * 查询用户已配置的 API Key 列表。
     *
     * @param userId 用户 ID
     * @return 用户密钥集合
     */
    @Override
    public List<ModelUserApiKey> listUserKeys(Long userId) {
        return modelMapper.listUserKeys(userId);
    }

    @Override
    public List<ModelOfficialApiKey> listOfficialKeys() {
        return modelMapper.listOfficialKeys();
    }

    /**
     * 新增用户模型密钥。
     *
     * @param userId 用户 ID
     * @param providerId 提供商 ID
     * @param keyName 密钥展示名
     * @param encryptedApiKey 加密后的 API Key
     * @param maskedApiKey 脱敏后的 API Key
     * @param isDefault 是否默认
     * @param status 密钥状态
     * @return 受影响行数
     */
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

    /**
     * 清空用户当前默认密钥标记。
     *
     * @param userId 用户 ID
     * @return 受影响行数
     */
    @Override
    public int clearDefaultUserKey(Long userId) {
        return modelMapper.clearDefaultUserKey(userId);
    }

    @Override
    public int clearDefaultOfficialKey(Long providerId) {
        return modelMapper.clearDefaultOfficialKey(providerId);
    }

    /**
     * 更新用户模型密钥。
     *
     * @param userId 用户 ID
     * @param keyId 密钥 ID
     * @param keyName 密钥展示名
     * @param encryptedApiKey 加密后的 API Key
     * @param maskedApiKey 脱敏后的 API Key
     * @param isDefault 是否默认
     * @param status 密钥状态
     * @return 受影响行数
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

    @Override
    public int updateOfficialKey(Long keyId,
                                 String keyName,
                                 String encryptedApiKey,
                                 String maskedApiKey,
                                 Boolean isDefault,
                                 String status) {
        return modelMapper.updateOfficialKey(keyId, keyName, encryptedApiKey, maskedApiKey, isDefault, status);
    }

    /**
     * 逻辑删除用户密钥。
     *
     * @param userId 用户 ID
     * @param keyId 密钥 ID
     * @return 受影响行数
     */
    @Override
    public int softDeleteUserKey(Long userId, Long keyId) {
        return modelMapper.softDeleteUserKey(userId, keyId);
    }

    @Override
    public int softDeleteOfficialKey(Long keyId) {
        return modelMapper.softDeleteOfficialKey(keyId);
    }

    /**
     * 查询项目下的模型策略列表。
     *
     * @param projectId 项目 ID
     * @return 模型策略集合
     */
    @Override
    public List<ModelProjectPolicy> listProjectPolicies(Long projectId) {
        return modelMapper.listProjectPolicies(projectId);
    }

    @Override
    public ModelProjectPolicy findProjectPolicy(Long projectId, Long policyId) {
        return modelMapper.findProjectPolicy(projectId, policyId);
    }

    @Override
    public ModelProjectPolicy findDefaultProjectPolicy(Long projectId) {
        return modelMapper.findDefaultProjectPolicy(projectId);
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

    /**
     * 新增项目模型策略。
     *
     * @param projectId 项目 ID
     * @param policyName 策略名称
     * @param scene 应用场景
     * @param providerModelId 提供商模型 ID
     * @param userKeyId 用户密钥 ID
     * @param temperature 温度参数
     * @param topP Top-P 参数
     * @param maxTokens 最大输出 Token 数
     * @param fallbackPolicyJson 兜底策略 JSON
     * @param isDefault 是否默认
     * @return 受影响行数
     */
    @Override
    public int insertPolicy(Long projectPolicyId,
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
                            boolean isDefault) {
        return modelMapper.insertPolicy(projectPolicyId, projectId, policyName, scene, providerModelId, modelName, baseUrl, userKeyId, officialKeyId, temperature, topP, maxTokens, fallbackPolicyJson, isDefault);
    }

    /**
     * 更新项目模型策略。
     *
     * @param projectId 项目 ID
     * @param policyId 策略 ID
     * @param policyName 策略名称
     * @param scene 应用场景
     * @param providerModelId 提供商模型 ID
     * @param userKeyId 用户密钥 ID
     * @param temperature 温度参数
     * @param topP Top-P 参数
     * @param maxTokens 最大输出 Token 数
     * @param fallbackPolicyJson 兜底策略 JSON
     * @param isDefault 是否默认
     * @return 受影响行数
     */
    @Override
    public int updatePolicy(Long projectId,
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
                            Boolean isDefault) {
        return modelMapper.updatePolicy(projectId, policyId, policyName, scene, providerModelId, modelName, baseUrl, userKeyId, officialKeyId, temperature, topP, maxTokens, fallbackPolicyJson, isDefault);
    }

    /**
     * 逻辑删除项目模型策略。
     *
     * @param projectId 项目 ID
     * @param policyId 策略 ID
     * @return 受影响行数
     */
    @Override
    public int softDeletePolicy(Long projectId, Long policyId) {
        return modelMapper.softDeletePolicy(projectId, policyId);
    }

    /**
     * 清空项目当前默认策略标记。
     *
     * @param projectId 项目 ID
     * @return 受影响行数
     */
    @Override
    public int clearDefaultPolicy(Long projectId) {
        return modelMapper.clearDefaultPolicy(projectId);
    }

    /**
     * 将指定策略设置为项目默认策略。
     *
     * @param projectId 项目 ID
     * @param policyId 策略 ID
     * @return 受影响行数
     */
    @Override
    public int setDefaultPolicy(Long projectId, Long policyId) {
        return modelMapper.setDefaultPolicy(projectId, policyId);
    }

    @Override
    public List<Map<String, Object>> listUserModelConfigs(Long userId) {
        return modelMapper.listUserModelConfigs(userId);
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
