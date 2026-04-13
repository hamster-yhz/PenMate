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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ModelApplicationService {

    private final ModelRepository modelRepository;
    private final AuditService auditService;

    public ModelApplicationService(ModelRepository modelRepository,
                                   AuditService auditService) {
        this.modelRepository = modelRepository;
        this.auditService = auditService;
    }

    public List<ModelProvider> listProviders() {
        return modelRepository.listProviders();
    }

    public List<ModelProviderModel> listProviderModels(String providerCode) {
        return modelRepository.listProviderModels(providerCode);
    }

    public List<ModelUserApiKey> listUserKeys(Long userId) {
        return modelRepository.listUserKeys(userId);
    }

    public void createKey(Long userId, CreateModelKeyCommand command, String traceId) {
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
            throw new IllegalArgumentException("Failed to create model key");
        }
        writeAudit(traceId, command.operatorId(), "model", "create-model-key", "model_user_api_keys", userId.toString(), null, 200);
    }

    public void updateKey(Long userId, Long keyId, UpdateModelKeyCommand command, String traceId) {
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
            throw new IllegalArgumentException("Model key not found");
        }
        writeAudit(traceId, command.operatorId(), "model", "update-model-key", "model_user_api_keys", keyId.toString(), null, 200);
    }

    public void deleteKey(Long userId, Long keyId, Long operatorId, String traceId) {
        int affected = modelRepository.softDeleteUserKey(userId, keyId);
        if (affected != 1) {
            throw new IllegalArgumentException("Model key not found");
        }
        writeAudit(traceId, operatorId, "model", "delete-model-key", "model_user_api_keys", keyId.toString(), null, 200);
    }

    public List<ModelProjectPolicy> listPolicies(Long projectId) {
        return modelRepository.listProjectPolicies(projectId);
    }

    public void createPolicy(Long projectId, CreatePolicyCommand command, String traceId) {
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
            throw new IllegalArgumentException("Failed to create model policy");
        }
        writeAudit(traceId, command.operatorId(), "model", "create-model-policy", "model_project_policies", projectId.toString(), null, 200);
    }

    public void updatePolicy(Long projectId, Long policyId, UpdatePolicyCommand command, String traceId) {
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
            throw new IllegalArgumentException("Model policy not found");
        }
        writeAudit(traceId, command.operatorId(), "model", "update-model-policy", "model_project_policies", policyId.toString(), null, 200);
    }

    public void deletePolicy(Long projectId, Long policyId, Long operatorId, String traceId) {
        int affected = modelRepository.softDeletePolicy(projectId, policyId);
        if (affected != 1) {
            throw new IllegalArgumentException("Model policy not found");
        }
        writeAudit(traceId, operatorId, "model", "delete-model-policy", "model_project_policies", policyId.toString(), null, 200);
    }

    public void setDefaultPolicy(Long projectId, Long policyId, Long operatorId, String traceId) {
        modelRepository.clearDefaultPolicy(projectId);
        int affected = modelRepository.setDefaultPolicy(projectId, policyId);
        if (affected != 1) {
            throw new IllegalArgumentException("Model policy not found");
        }
        writeAudit(traceId, operatorId, "model", "set-default-model-policy", "model_project_policies", policyId.toString(), null, 200);
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

