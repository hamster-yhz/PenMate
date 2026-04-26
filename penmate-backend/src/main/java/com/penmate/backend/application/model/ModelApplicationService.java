package com.penmate.backend.application.model;

import com.penmate.backend.application.model.command.ModelCommands.CreateModelKeyCommand;
import com.penmate.backend.application.model.command.ModelCommands.CreateOfficialModelKeyCommand;
import com.penmate.backend.application.model.command.ModelCommands.CreatePolicyCommand;
import com.penmate.backend.application.model.command.ModelCommands.UpdateModelKeyCommand;
import com.penmate.backend.application.model.command.ModelCommands.UpdateOfficialModelKeyCommand;
import com.penmate.backend.application.model.command.ModelCommands.UpdatePolicyCommand;
import com.penmate.backend.domain.model.model.ModelOfficialApiKey;
import com.penmate.backend.domain.model.model.ModelProjectPolicy;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelUserApiKey;
import com.penmate.backend.domain.model.repository.ModelRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.shared.service.SecretCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 模型配置应用服务。
 * <p>负责模型厂商/模型查询、用户 API Key 管理、项目模型策略管理与默认策略切换。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ModelApplicationService {

    private final ModelRepository modelRepository;
    private final BusinessIdGenerator businessIdGenerator;
    private final SecretCryptoService secretCryptoService;

    /**
     * 查询可用模型厂商列表。
     *
     * @return 出参：处理结果
     */
    public List<ModelProvider> listProviders() {
        log.info("查询模型厂商列表");
        return BuiltinModelProviders.list();
    }

    /**
     * 查询用户已配置的模型密钥列表。
     *
     * @param userId 入参：userId
     * @return 出参：处理结果
     */
    public List<ModelUserApiKey> listUserKeys(Long userId) {
        log.info("查询用户模型密钥列表: userId={}", userId);
        return modelRepository.listUserKeys(userId);
    }

    public List<ModelOfficialApiKey> listOfficialKeys() {
        log.info("查询官方模型密钥列表");
        return modelRepository.listOfficialKeys();
    }

    /**
     * 新增用户模型密钥。
     *
     * @param userId 入参：userId
     * @param command 入参：command
     * @param traceId 入参：traceId
     */
    public void createKey(Long userId, CreateModelKeyCommand command, String traceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(command, "command must not be null");
        log.info("创建模型密钥: userId={}, providerId={}, keyName={}", userId, command.providerId(), command.keyName());
        boolean toDefault = Boolean.TRUE.equals(command.isDefault());
        if (toDefault) {
            modelRepository.clearDefaultUserKey(userId);
        }
        int affected = modelRepository.insertUserKey(
                businessIdGenerator.nextId(),
                userId,
                command.providerId(),
                command.keyName(),
                secretCryptoService.encrypt(command.apiKey()),
                mask(command.apiKey()),
                toDefault,
                command.status() == null ? "active" : command.status()
        );
        if (affected < 1) {
            log.error("创建模型密钥失败: userId={}, providerId={}", userId, command.providerId());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create model key");
        }
        writeAudit(traceId, command.operatorId(), "model", "create-model-key", "model_user_api_keys", userId.toString(), null, 200);
        log.info("创建模型密钥成功: userId={}, keyName={}", userId, command.keyName());
    }

    /**
     * 更新用户模型密钥。
     *
     * @param userId 入参：userId
     * @param keyId 入参：keyId
     * @param command 入参：command
     * @param traceId 入参：traceId
     */
    public void updateKey(Long userId, Long keyId, UpdateModelKeyCommand command, String traceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
        Objects.requireNonNull(command, "command must not be null");
        log.info("更新模型密钥: userId={}, keyId={}", userId, keyId);
        if (Boolean.TRUE.equals(command.isDefault())) {
            modelRepository.clearDefaultUserKey(userId);
        }
        String encrypted = command.apiKey() == null || command.apiKey().isBlank() ? null : secretCryptoService.encrypt(command.apiKey());
        String masked = command.apiKey() == null || command.apiKey().isBlank() ? null : mask(command.apiKey());
        int affected = modelRepository.updateUserKey(
                userId,
                keyId,
                command.keyName(),
                encrypted,
                masked,
                command.isDefault(),
                command.status()
        );
        if (affected != 1) {
            log.warn("更新模型密钥失败: userId={}, keyId={}, reason=not_found", userId, keyId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Model key not found");
        }
        writeAudit(traceId, command.operatorId(), "model", "update-model-key", "model_user_api_keys", keyId.toString(), null, 200);
        log.info("更新模型密钥成功: userId={}, keyId={}", userId, keyId);
    }

    /**
     * 软删除用户模型密钥。
     *
     * @param userId 入参：userId
     * @param keyId 入参：keyId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void deleteKey(Long userId, Long keyId, Long operatorId, String traceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
        Objects.requireNonNull(operatorId, "operatorId must not be null");
        log.info("删除模型密钥: userId={}, keyId={}", userId, keyId);
        int affected = modelRepository.softDeleteUserKey(userId, keyId);
        if (affected != 1) {
            log.warn("删除模型密钥失败: userId={}, keyId={}, reason=not_found", userId, keyId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Model key not found");
        }
        writeAudit(traceId, operatorId, "model", "delete-model-key", "model_user_api_keys", keyId.toString(), null, 200);
        log.info("删除模型密钥成功: userId={}, keyId={}", userId, keyId);
    }

    public void createOfficialKey(CreateOfficialModelKeyCommand command, String traceId) {
        Objects.requireNonNull(command, "command must not be null");
        log.info("创建官方模型密钥: providerId={}, keyName={}", command.providerId(), command.keyName());
        boolean toDefault = Boolean.TRUE.equals(command.isDefault());
        if (toDefault) {
            modelRepository.clearDefaultOfficialKey(command.providerId());
        }
        int affected = modelRepository.insertOfficialKey(
                businessIdGenerator.nextId(),
                command.providerId(),
                command.keyName(),
                secretCryptoService.encrypt(command.apiKey()),
                mask(command.apiKey()),
                toDefault,
                command.status() == null ? "active" : command.status()
        );
        if (affected < 1) {
            log.error("创建官方模型密钥失败: providerId={}", command.providerId());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create official model key");
        }
        writeAudit(traceId, command.operatorId(), "model", "create-official-model-key", "model_official_api_keys", command.providerId().toString(), null, 200);
        log.info("创建官方模型密钥成功: providerId={}, keyName={}", command.providerId(), command.keyName());
    }

    public void updateOfficialKey(Long keyId, UpdateOfficialModelKeyCommand command, String traceId) {
        Objects.requireNonNull(command, "command must not be null");
        log.info("更新官方模型密钥: keyId={}", keyId);
        ModelOfficialApiKey existing = modelRepository.findOfficialKey(keyId);
        if (existing == null) {
            log.warn("更新官方模型密钥失败: keyId={}, reason=not_found", keyId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Official model key not found");
        }
        if (Boolean.TRUE.equals(command.isDefault())) {
            modelRepository.clearDefaultOfficialKey(existing.getProviderId());
        }
        String encrypted = command.apiKey() == null || command.apiKey().isBlank() ? null : secretCryptoService.encrypt(command.apiKey());
        String masked = command.apiKey() == null || command.apiKey().isBlank() ? null : mask(command.apiKey());
        int affected = modelRepository.updateOfficialKey(
                keyId,
                command.keyName(),
                encrypted,
                masked,
                command.isDefault(),
                command.status()
        );
        if (affected != 1) {
            log.warn("更新官方模型密钥失败: keyId={}, reason=not_found", keyId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Official model key not found");
        }
        writeAudit(traceId, command.operatorId(), "model", "update-official-model-key", "model_official_api_keys", keyId.toString(), null, 200);
        log.info("更新官方模型密钥成功: keyId={}", keyId);
    }

    public void deleteOfficialKey(Long keyId, Long operatorId, String traceId) {
        log.info("删除官方模型密钥: keyId={}", keyId);
        int affected = modelRepository.softDeleteOfficialKey(keyId);
        if (affected != 1) {
            log.warn("删除官方模型密钥失败: keyId={}, reason=not_found", keyId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Official model key not found");
        }
        writeAudit(traceId, operatorId, "model", "delete-official-model-key", "model_official_api_keys", keyId.toString(), null, 200);
        log.info("删除官方模型密钥成功: keyId={}", keyId);
    }

    /**
     * 查询项目模型策略列表。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<ModelProjectPolicy> listPolicies(Long projectId) {
        log.info("查询项目模型策略列表: projectId={}", projectId);
        return modelRepository.listProjectPolicies(projectId);
    }

    /**
     * 新增项目模型策略。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param traceId 入参：traceId
     */
    public void createPolicy(Long projectId, CreatePolicyCommand command, String traceId) {
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(command, "command must not be null");
        log.info("创建模型策略: projectId={}, policyName={}, scene={}", projectId, command.policyName(), command.scene());
        boolean toDefault = Boolean.TRUE.equals(command.isDefault());
        if (toDefault) {
            modelRepository.clearDefaultPolicy(projectId);
        }
        int affected = modelRepository.insertPolicy(
                businessIdGenerator.nextId(),
                projectId,
                command.policyName(),
                command.scene(),
                command.providerModelId(),
                command.modelName(),
                command.baseUrl(),
                command.userKeyId(),
                command.officialKeyId(),
                command.temperature(),
                command.topP(),
                command.maxTokens(),
                command.fallbackPolicyJson(),
                toDefault
        );
        if (affected < 1) {
            log.error("创建模型策略失败: projectId={}, policyName={}", projectId, command.policyName());
            throw com.penmate.backend.application.common.exception.BusinessException.of("Failed to create model policy");
        }
        writeAudit(traceId, command.operatorId(), "model", "create-model-policy", "model_project_policies", projectId.toString(), null, 200);
        log.info("创建模型策略成功: projectId={}, policyName={}", projectId, command.policyName());
    }

    /**
     * 更新项目模型策略。
     *
     * @param projectId 入参：projectId
     * @param policyId 入参：policyId
     * @param command 入参：command
     * @param traceId 入参：traceId
     */
    public void updatePolicy(Long projectId, Long policyId, UpdatePolicyCommand command, String traceId) {
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(command, "command must not be null");
        log.info("更新模型策略: projectId={}, policyId={}", projectId, policyId);
        if (Boolean.TRUE.equals(command.isDefault())) {
            modelRepository.clearDefaultPolicy(projectId);
        }
        int affected = modelRepository.updatePolicy(
                projectId,
                policyId,
                command.policyName(),
                command.scene(),
                command.providerModelId(),
                command.modelName(),
                command.baseUrl(),
                command.userKeyId(),
                command.officialKeyId(),
                command.temperature(),
                command.topP(),
                command.maxTokens(),
                command.fallbackPolicyJson(),
                command.isDefault()
        );
        if (affected != 1) {
            log.warn("更新模型策略失败: projectId={}, policyId={}, reason=not_found", projectId, policyId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Model policy not found");
        }
        writeAudit(traceId, command.operatorId(), "model", "update-model-policy", "model_project_policies", policyId.toString(), null, 200);
        log.info("更新模型策略成功: projectId={}, policyId={}", projectId, policyId);
    }

    /**
     * 软删除项目模型策略。
     *
     * @param projectId 入参：projectId
     * @param policyId 入参：policyId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void deletePolicy(Long projectId, Long policyId, Long operatorId, String traceId) {
        log.info("删除模型策略: projectId={}, policyId={}", projectId, policyId);
        int affected = modelRepository.softDeletePolicy(projectId, policyId);
        if (affected != 1) {
            log.warn("删除模型策略失败: projectId={}, policyId={}, reason=not_found", projectId, policyId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Model policy not found");
        }
        writeAudit(traceId, operatorId, "model", "delete-model-policy", "model_project_policies", policyId.toString(), null, 200);
        log.info("删除模型策略成功: projectId={}, policyId={}", projectId, policyId);
    }

    /**
     * 设置项目默认模型策略。
     *
     * @param projectId 入参：projectId
     * @param policyId 入参：policyId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void setDefaultPolicy(Long projectId, Long policyId, Long operatorId, String traceId) {
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(policyId, "policyId must not be null");
        Objects.requireNonNull(operatorId, "operatorId must not be null");
        log.info("设置默认模型策略: projectId={}, policyId={}", projectId, policyId);

        ModelProjectPolicy existing = modelRepository.findProjectPolicy(projectId, policyId);
        if (existing == null) {
            log.warn("设置默认模型策略失败: projectId={}, policyId={}, reason=not_found", projectId, policyId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Model policy not found");
        }

        modelRepository.clearDefaultPolicy(projectId);
        int affected = modelRepository.setDefaultPolicy(projectId, policyId);
        if (affected != 1) {
            log.warn("设置默认模型策略失败: projectId={}, policyId={}, reason=not_found", projectId, policyId);
            throw com.penmate.backend.application.common.exception.BusinessException.of("Model policy not found");
        }
        writeAudit(traceId, operatorId, "model", "set-default-model-policy", "model_project_policies", policyId.toString(), null, 200);
        log.info("设置默认模型策略成功: projectId={}, policyId={}", projectId, policyId);
    }


    private String mask(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        int keep = Math.min(4, key.length());
        return "****" + key.substring(key.length() - keep);
    }

    private void writeAudit(String traceId,
                            Long userId,
                            String module,
                            String action,
                            String resourceType,
                            String resourceId,
                            String requestJson,
                            int responseCode) {
        // 审计模块已移除
    }
}


