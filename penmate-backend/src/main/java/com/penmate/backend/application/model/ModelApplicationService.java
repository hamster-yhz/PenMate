package com.penmate.backend.application.model;

import com.penmate.backend.application.model.command.ModelCommands.CreateModelKeyCommand;
import com.penmate.backend.application.model.command.ModelCommands.CreatePolicyCommand;
import com.penmate.backend.application.model.command.ModelCommands.UpdateModelKeyCommand;
import com.penmate.backend.application.model.command.ModelCommands.UpdatePolicyCommand;
import com.penmate.backend.domain.model.model.ModelProjectPolicy;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelProviderModel;
import com.penmate.backend.domain.model.model.ModelUserApiKey;
import com.penmate.backend.domain.model.repository.ModelRepository;
import com.penmate.backend.domain.shared.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * ModelApplicationService。
 * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ModelApplicationService {

    private final ModelRepository modelRepository;
    private final AuditService auditService;

    /**
     * 查询列表数据。
     *
     * @return 出参：处理结果
     */
    public List<ModelProvider> listProviders() {
        log.info("查询模型厂商列表");
        return modelRepository.listProviders();
    }

    /**
     * 查询列表数据。
     *
     * @param providerCode 入参：providerCode
     * @return 出参：处理结果
     */
    public List<ModelProviderModel> listProviderModels(String providerCode) {
        log.info("查询厂商模型列表: providerCode={}", providerCode);
        return modelRepository.listProviderModels(providerCode);
    }

    /**
     * 查询列表数据。
     *
     * @param userId 入参：userId
     * @return 出参：处理结果
     */
    public List<ModelUserApiKey> listUserKeys(Long userId) {
        log.info("查询用户模型密钥列表: userId={}", userId);
        return modelRepository.listUserKeys(userId);
    }

    /**
     * 创建业务数据。
     *
     * @param userId 入参：userId
     * @param command 入参：command
     * @param traceId 入参：traceId
     */
    public void createKey(Long userId, CreateModelKeyCommand command, String traceId) {
        log.info("创建模型密钥: userId={}, providerId={}, keyName={}", userId, command.providerId(), command.keyName());
        boolean toDefault = Boolean.TRUE.equals(command.isDefault());
        if (toDefault) {
            modelRepository.clearDefaultUserKey(userId);
        }
        int affected = modelRepository.insertUserKey(
                userId,
                command.providerId(),
                command.keyName(),
                encrypt(command.apiKey()),
                mask(command.apiKey()),
                toDefault,
                command.status() == null ? "active" : command.status()
        );
        if (affected < 1) {
            log.error("创建模型密钥失败: userId={}, providerId={}", userId, command.providerId());
            throw new IllegalArgumentException("Failed to create model key");
        }
        writeAudit(traceId, command.operatorId(), "model", "create-model-key", "model_user_api_keys", userId.toString(), null, 200);
        log.info("创建模型密钥成功: userId={}, keyName={}", userId, command.keyName());
    }

    /**
     * 更新业务数据。
     *
     * @param userId 入参：userId
     * @param keyId 入参：keyId
     * @param command 入参：command
     * @param traceId 入参：traceId
     */
    public void updateKey(Long userId, Long keyId, UpdateModelKeyCommand command, String traceId) {
        log.info("更新模型密钥: userId={}, keyId={}", userId, keyId);
        if (Boolean.TRUE.equals(command.isDefault())) {
            modelRepository.clearDefaultUserKey(userId);
        }
        String encrypted = command.apiKey() == null || command.apiKey().isBlank() ? null : encrypt(command.apiKey());
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
            throw new IllegalArgumentException("Model key not found");
        }
        writeAudit(traceId, command.operatorId(), "model", "update-model-key", "model_user_api_keys", keyId.toString(), null, 200);
        log.info("更新模型密钥成功: userId={}, keyId={}", userId, keyId);
    }

    /**
     * 删除业务数据。
     *
     * @param userId 入参：userId
     * @param keyId 入参：keyId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void deleteKey(Long userId, Long keyId, Long operatorId, String traceId) {
        log.info("删除模型密钥: userId={}, keyId={}", userId, keyId);
        int affected = modelRepository.softDeleteUserKey(userId, keyId);
        if (affected != 1) {
            log.warn("删除模型密钥失败: userId={}, keyId={}, reason=not_found", userId, keyId);
            throw new IllegalArgumentException("Model key not found");
        }
        writeAudit(traceId, operatorId, "model", "delete-model-key", "model_user_api_keys", keyId.toString(), null, 200);
        log.info("删除模型密钥成功: userId={}, keyId={}", userId, keyId);
    }

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    public List<ModelProjectPolicy> listPolicies(Long projectId) {
        log.info("查询项目模型策略列表: projectId={}", projectId);
        return modelRepository.listProjectPolicies(projectId);
    }

    /**
     * 创建业务数据。
     *
     * @param projectId 入参：projectId
     * @param command 入参：command
     * @param traceId 入参：traceId
     */
    public void createPolicy(Long projectId, CreatePolicyCommand command, String traceId) {
        log.info("创建模型策略: projectId={}, policyName={}, scene={}", projectId, command.policyName(), command.scene());
        boolean toDefault = Boolean.TRUE.equals(command.isDefault());
        if (toDefault) {
            modelRepository.clearDefaultPolicy(projectId);
        }
        int affected = modelRepository.insertPolicy(
                projectId,
                command.policyName(),
                command.scene(),
                command.providerModelId(),
                command.userKeyId(),
                command.temperature(),
                command.topP(),
                command.maxTokens(),
                command.fallbackPolicyJson(),
                toDefault
        );
        if (affected < 1) {
            log.error("创建模型策略失败: projectId={}, policyName={}", projectId, command.policyName());
            throw new IllegalArgumentException("Failed to create model policy");
        }
        writeAudit(traceId, command.operatorId(), "model", "create-model-policy", "model_project_policies", projectId.toString(), null, 200);
        log.info("创建模型策略成功: projectId={}, policyName={}", projectId, command.policyName());
    }

    /**
     * 更新业务数据。
     *
     * @param projectId 入参：projectId
     * @param policyId 入参：policyId
     * @param command 入参：command
     * @param traceId 入参：traceId
     */
    public void updatePolicy(Long projectId, Long policyId, UpdatePolicyCommand command, String traceId) {
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
                command.userKeyId(),
                command.temperature(),
                command.topP(),
                command.maxTokens(),
                command.fallbackPolicyJson(),
                command.isDefault()
        );
        if (affected != 1) {
            log.warn("更新模型策略失败: projectId={}, policyId={}, reason=not_found", projectId, policyId);
            throw new IllegalArgumentException("Model policy not found");
        }
        writeAudit(traceId, command.operatorId(), "model", "update-model-policy", "model_project_policies", policyId.toString(), null, 200);
        log.info("更新模型策略成功: projectId={}, policyId={}", projectId, policyId);
    }

    /**
     * 删除业务数据。
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
            throw new IllegalArgumentException("Model policy not found");
        }
        writeAudit(traceId, operatorId, "model", "delete-model-policy", "model_project_policies", policyId.toString(), null, 200);
        log.info("删除模型策略成功: projectId={}, policyId={}", projectId, policyId);
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param policyId 入参：policyId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     */
    public void setDefaultPolicy(Long projectId, Long policyId, Long operatorId, String traceId) {
        log.info("设置默认模型策略: projectId={}, policyId={}", projectId, policyId);
        modelRepository.clearDefaultPolicy(projectId);
        int affected = modelRepository.setDefaultPolicy(projectId, policyId);
        if (affected != 1) {
            log.warn("设置默认模型策略失败: projectId={}, policyId={}, reason=not_found", projectId, policyId);
            throw new IllegalArgumentException("Model policy not found");
        }
        writeAudit(traceId, operatorId, "model", "set-default-model-policy", "model_project_policies", policyId.toString(), null, 200);
        log.info("设置默认模型策略成功: projectId={}, policyId={}", projectId, policyId);
    }

    private String encrypt(String plain) {
        return plain == null ? null : "ENC(" + plain + ")";
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
        String finalTraceId = (traceId == null || traceId.isBlank()) ? UUID.randomUUID().toString() : traceId;
        auditService.write(finalTraceId, userId, module, action, resourceType, resourceId, requestJson, responseCode);
    }
}

