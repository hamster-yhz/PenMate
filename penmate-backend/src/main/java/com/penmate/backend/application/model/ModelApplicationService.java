package com.penmate.backend.application.model;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.model.command.ModelCommands.CreateModelKeyCommand;
import com.penmate.backend.application.model.command.ModelCommands.CreateOfficialModelKeyCommand;
import com.penmate.backend.application.model.command.ModelCommands.CreateUserModelConfigCommand;
import com.penmate.backend.application.model.command.ModelCommands.SaveUserModelPreferencesCommand;
import com.penmate.backend.application.model.command.ModelCommands.UpdateModelKeyCommand;
import com.penmate.backend.application.model.command.ModelCommands.UpdateOfficialModelKeyCommand;
import com.penmate.backend.application.model.command.ModelCommands.UpdateUserModelConfigCommand;
import com.penmate.backend.domain.iam.model.IamUser;
import com.penmate.backend.domain.iam.repository.IamGateway;
import com.penmate.backend.domain.model.model.ModelOfficialApiKey;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelUserApiKey;
import com.penmate.backend.domain.model.repository.ModelRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.shared.service.SecretCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 模型配置应用服务。
 * <p>负责模型厂商/模型查询、用户 API Key 管理、用户模型配置管理与角色偏好切换。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ModelApplicationService {

    private static final String USER_KEY_SOURCE = "USER_KEY";
    private static final String OFFICIAL_KEY_SOURCE = "OFFICIAL_KEY";

    private final ModelRepository modelRepository;
    private final BusinessIdGenerator businessIdGenerator;
    private final SecretCryptoService secretCryptoService;
    private final IamGateway iamGateway;

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
     * 查询用户模型偏好详情。
     *
     * @param userId 入参：userId
     * @return 出参：当前偏好与候选配置列表
     */
    public Map<String, Object> getUserModelPreferencesDetail(Long userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        log.info("查询用户模型偏好详情: userId={}", userId);

        IamUser user = iamGateway.findUserByUserId(userId);
        if (user == null) {
            log.warn("查询用户模型偏好详情失败: userId={}, reason=user_not_found", userId);
            throw BusinessException.of("User not found");
        }

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("mainAgentModelConfigId", user.getMainAgentModelConfigId());
        result.put("dirtyWorkAgentModelConfigId", user.getDirtyWorkAgentModelConfigId());
        result.put("candidateConfigs", modelRepository.listUserModelConfigs(userId));
        return result;
    }

    public List<Map<String, Object>> listUserModelConfigs(Long userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        log.info("查询用户模型配置列表: userId={}", userId);
        return modelRepository.listUserModelConfigs(userId);
    }

    public void createUserModelConfig(Long userId, CreateUserModelConfigCommand command, String traceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(command, "command must not be null");

        Long providerId = validateProviderAndModelName(command.providerId(), command.modelName());
        Long modelConfigId = businessIdGenerator.nextId();
        KeyBinding keyBinding = resolveKeyBindingForCreate(
                userId,
                modelConfigId,
                providerId,
                command.modelName(),
                command.keySourceType(),
                command.apiKey(),
                command.status()
        );

        int affected = modelRepository.insertUserModelConfig(
                modelConfigId,
                userId,
                providerId,
                normalize(command.modelName()),
                normalizeNullable(command.baseUrl()),
                keyBinding.keySourceType(),
                keyBinding.userKeyId(),
                keyBinding.officialKeyId(),
                normalizeStatus(command.status())
        );
        if (affected != 1) {
            throw BusinessException.of("Failed to create user model config");
        }
        writeAudit(traceId, command.operatorId(), "model", "create-user-model-config", "model_user_configurations", modelConfigId.toString(), null, 200);
    }

    public void updateUserModelConfig(Long userId, Long modelConfigId, UpdateUserModelConfigCommand command, String traceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(modelConfigId, "modelConfigId must not be null");
        Objects.requireNonNull(command, "command must not be null");

        Map<String, Object> existing = modelRepository.findUserModelConfig(userId, modelConfigId);
        if (existing == null) {
            throw BusinessException.of("User model config not found");
        }

        Long mergedProviderId = command.providerId() != null ? command.providerId() : longValue(existing.get("providerId"));
        String mergedModelName = command.modelName() != null ? normalize(command.modelName()) : stringValue(existing.get("modelName"));
        String mergedBaseUrl = command.baseUrl() != null
                ? normalizeNullable(command.baseUrl())
                : normalizeNullable(stringValue(existing.get("baseUrl")));
        String mergedKeySourceType = command.keySourceType() != null
                ? normalizeKeySourceType(command.keySourceType())
                : stringValue(existing.get("keySourceType"));
        String mergedStatus = command.status() != null ? normalizeStatus(command.status()) : stringValue(existing.get("status"));

        validateProviderAndModelName(mergedProviderId, mergedModelName);
        KeyBinding keyBinding = resolveKeyBindingForUpdate(
                userId,
                modelConfigId,
                mergedProviderId,
                mergedModelName,
                mergedKeySourceType,
                command.apiKey(),
                mergedStatus,
                existing
        );

        int affected = modelRepository.updateUserModelConfig(
                userId,
                modelConfigId,
                mergedProviderId,
                mergedModelName,
                mergedBaseUrl,
                keyBinding.keySourceType(),
                keyBinding.userKeyId(),
                keyBinding.officialKeyId(),
                mergedStatus
        );
        if (affected != 1) {
            throw BusinessException.of("User model config not found");
        }
        writeAudit(traceId, command.operatorId(), "model", "update-user-model-config", "model_user_configurations", modelConfigId.toString(), null, 200);
    }

    public void deleteUserModelConfig(Long userId, Long modelConfigId, Long operatorId, String traceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(modelConfigId, "modelConfigId must not be null");
        Objects.requireNonNull(operatorId, "operatorId must not be null");
        int affected = modelRepository.softDeleteUserModelConfig(userId, modelConfigId);
        if (affected != 1) {
            throw BusinessException.of("User model config not found");
        }
        writeAudit(traceId, operatorId, "model", "delete-user-model-config", "model_user_configurations", modelConfigId.toString(), null, 200);
    }

    public void saveUserModelPreferences(Long userId,
                                         Long operatorId,
                                         SaveUserModelPreferencesCommand command,
                                         String traceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(operatorId, "operatorId must not be null");
        Objects.requireNonNull(command, "command must not be null");
        log.info("保存用户模型偏好: userId={}, operatorId={}, mainAgentModelConfigId={}, dirtyWorkAgentModelConfigId={}",
                userId,
                operatorId,
                command.mainAgentModelConfigId(),
                command.dirtyWorkAgentModelConfigId());

        IamUser user = iamGateway.findUserByUserId(userId);
        if (user == null) {
            log.warn("保存用户模型偏好失败: userId={}, reason=user_not_found", userId);
            throw BusinessException.of("User not found");
        }

        if (command.mainAgentModelConfigId() != null
                && !modelRepository.existsUsableModelConfig(userId, command.mainAgentModelConfigId())) {
            log.warn("保存用户模型偏好失败: userId={}, mainAgentModelConfigId={}, reason=model_config_unusable",
                    userId,
                    command.mainAgentModelConfigId());
            throw BusinessException.of("Main agent model config is unavailable");
        }
        if (command.dirtyWorkAgentModelConfigId() != null
                && !modelRepository.existsUsableModelConfig(userId, command.dirtyWorkAgentModelConfigId())) {
            log.warn("保存用户模型偏好失败: userId={}, dirtyWorkAgentModelConfigId={}, reason=model_config_unusable",
                    userId,
                    command.dirtyWorkAgentModelConfigId());
            throw BusinessException.of("Dirty work agent model config is unavailable");
        }

        int affected = modelRepository.updateUserModelPreferences(
                userId,
                command.mainAgentModelConfigId(),
                command.dirtyWorkAgentModelConfigId()
        );
        if (affected != 1) {
            log.warn("保存用户模型偏好失败: userId={}, reason=update_failed", userId);
            throw BusinessException.of("Failed to update user model preferences");
        }
        writeAudit(traceId, operatorId, "model", "save-user-model-preferences", "iam_users", userId.toString(), null, 200);
        log.info("保存用户模型偏好成功: userId={}", userId);
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
        Long providerId = validateProviderId(command.providerId());
        log.info("创建模型密钥: userId={}, providerId={}, keyName={}", userId, providerId, command.keyName());
        boolean toDefault = Boolean.TRUE.equals(command.isDefault());
        if (toDefault) {
            modelRepository.clearDefaultUserKey(userId);
        }
        int affected = modelRepository.insertUserKey(
                businessIdGenerator.nextId(),
                userId,
                providerId,
                command.keyName(),
                secretCryptoService.encrypt(command.apiKey()),
                mask(command.apiKey()),
                toDefault,
                command.status() == null ? "active" : command.status()
        );
        if (affected < 1) {
            log.error("创建模型密钥失败: userId={}, providerId={}", userId, command.providerId());
            throw BusinessException.of("Failed to create model key");
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
            throw BusinessException.of("Model key not found");
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
            throw BusinessException.of("Model key not found");
        }
        writeAudit(traceId, operatorId, "model", "delete-model-key", "model_user_api_keys", keyId.toString(), null, 200);
        log.info("删除模型密钥成功: userId={}, keyId={}", userId, keyId);
    }

    public void createOfficialKey(CreateOfficialModelKeyCommand command, String traceId) {
        Objects.requireNonNull(command, "command must not be null");
        Long providerId = validateProviderId(command.providerId());
        log.info("创建官方模型密钥: providerId={}, keyName={}", providerId, command.keyName());
        boolean toDefault = Boolean.TRUE.equals(command.isDefault());
        if (toDefault) {
            modelRepository.clearDefaultOfficialKey(providerId);
        }
        int affected = modelRepository.insertOfficialKey(
                businessIdGenerator.nextId(),
                providerId,
                command.keyName(),
                secretCryptoService.encrypt(command.apiKey()),
                mask(command.apiKey()),
                toDefault,
                command.status() == null ? "active" : command.status()
        );
        if (affected < 1) {
            log.error("创建官方模型密钥失败: providerId={}", providerId);
            throw BusinessException.of("Failed to create official model key");
        }
        writeAudit(traceId, command.operatorId(), "model", "create-official-model-key", "model_official_api_keys", providerId.toString(), null, 200);
        log.info("创建官方模型密钥成功: providerId={}, keyName={}", providerId, command.keyName());
    }

    public void updateOfficialKey(Long keyId, UpdateOfficialModelKeyCommand command, String traceId) {
        Objects.requireNonNull(command, "command must not be null");
        log.info("更新官方模型密钥: keyId={}", keyId);
        ModelOfficialApiKey existing = modelRepository.findOfficialKey(keyId);
        if (existing == null) {
            log.warn("更新官方模型密钥失败: keyId={}, reason=not_found", keyId);
            throw BusinessException.of("Official model key not found");
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
            throw BusinessException.of("Official model key not found");
        }
        writeAudit(traceId, command.operatorId(), "model", "update-official-model-key", "model_official_api_keys", keyId.toString(), null, 200);
        log.info("更新官方模型密钥成功: keyId={}", keyId);
    }

    public void deleteOfficialKey(Long keyId, Long operatorId, String traceId) {
        log.info("删除官方模型密钥: keyId={}", keyId);
        int affected = modelRepository.softDeleteOfficialKey(keyId);
        if (affected != 1) {
            log.warn("删除官方模型密钥失败: keyId={}, reason=not_found", keyId);
            throw BusinessException.of("Official model key not found");
        }
        writeAudit(traceId, operatorId, "model", "delete-official-model-key", "model_official_api_keys", keyId.toString(), null, 200);
        log.info("删除官方模型密钥成功: keyId={}", keyId);
    }

    private Long validateProviderAndModelName(Long providerId, String modelName) {
        Long normalizedProviderId = validateProviderId(providerId);
        if (modelName == null || modelName.isBlank()) {
            throw BusinessException.of("Model name is required");
        }
        return normalizedProviderId;
    }

    private Long validateProviderId(Long providerId) {
        if (providerId == null) {
            throw BusinessException.of("Provider id is required");
        }
        if (providerId <= 0 || BuiltinModelProviders.findById(providerId).isEmpty()) {
            throw BusinessException.of("Provider id is invalid");
        }
        return providerId;
    }

    private KeyBinding resolveKeyBindingForCreate(Long userId,
                                                  Long generatedKeyId,
                                                  Long providerId,
                                                  String modelName,
                                                  String keySourceType,
                                                  String apiKey,
                                                  String status) {
        String normalizedKeySourceType = normalizeKeySourceType(keySourceType);
        String normalizedApiKey = normalizeApiKey(apiKey);
        if (OFFICIAL_KEY_SOURCE.equals(normalizedKeySourceType)) {
            int affected = modelRepository.insertOfficialKey(
                    generatedKeyId,
                    providerId,
                    buildGeneratedKeyName(modelName),
                    secretCryptoService.encrypt(normalizedApiKey),
                    mask(normalizedApiKey),
                    false,
                    normalizeStatus(status)
            );
            if (affected < 1) {
                throw BusinessException.of("Failed to create model key");
            }
            ModelOfficialApiKey officialKey = modelRepository.findOfficialKey(generatedKeyId);
            if (officialKey == null || !providerId.equals(officialKey.getProviderId())) {
                throw BusinessException.of("Official model key not found");
            }
            return new KeyBinding(normalizedKeySourceType, null, generatedKeyId);
        }

        int affected = modelRepository.insertUserKey(
                generatedKeyId,
                userId,
                providerId,
                buildGeneratedKeyName(modelName),
                secretCryptoService.encrypt(normalizedApiKey),
                mask(normalizedApiKey),
                false,
                normalizeStatus(status)
        );
        if (affected < 1) {
            throw BusinessException.of("Failed to create model key");
        }
        ModelUserApiKey userKey = modelRepository.findUserKey(generatedKeyId);
        if (userKey == null || !userId.equals(userKey.getUserId()) || !providerId.equals(userKey.getProviderId())) {
            throw BusinessException.of("User model key not found");
        }
        return new KeyBinding(normalizedKeySourceType, generatedKeyId, null);
    }

    private KeyBinding resolveKeyBindingForUpdate(Long userId,
                                                  Long generatedKeyId,
                                                  Long providerId,
                                                  String modelName,
                                                  String keySourceType,
                                                  String apiKey,
                                                  String status,
                                                  Map<String, Object> existing) {
        String effectiveKeySourceType = keySourceType == null ? stringValue(existing.get("keySourceType")) : keySourceType;
        String existingKeySourceType = stringValue(existing.get("keySourceType"));
        Long existingProviderId = longValue(existing.get("providerId"));
        String normalizedApiKey = normalizeNullable(apiKey);
        if (normalizedApiKey == null) {
            if (!Objects.equals(providerId, existingProviderId)
                    || !Objects.equals(effectiveKeySourceType, existingKeySourceType)) {
                throw BusinessException.of("Api key is required");
            }
            return new KeyBinding(
                    effectiveKeySourceType,
                    longValue(existing.get("userKeyId")),
                    longValue(existing.get("officialKeyId"))
            );
        }

        Long existingUserKeyId = longValue(existing.get("userKeyId"));
        Long existingOfficialKeyId = longValue(existing.get("officialKeyId"));
        String normalizedStatus = normalizeStatus(status);
        String encryptedApiKey = secretCryptoService.encrypt(normalizedApiKey);
        String maskedApiKey = mask(normalizedApiKey);

        if (USER_KEY_SOURCE.equals(effectiveKeySourceType)
                && USER_KEY_SOURCE.equals(existingKeySourceType)
                && providerId.equals(existingProviderId)
                && existingUserKeyId != null) {
            int affected = modelRepository.updateUserKey(
                    userId,
                    existingUserKeyId,
                    null,
                    encryptedApiKey,
                    maskedApiKey,
                    null,
                    normalizedStatus
            );
            if (affected != 1) {
                throw BusinessException.of("Model key not found");
            }
            return new KeyBinding(USER_KEY_SOURCE, existingUserKeyId, null);
        }

        if (OFFICIAL_KEY_SOURCE.equals(effectiveKeySourceType)
                && OFFICIAL_KEY_SOURCE.equals(existingKeySourceType)
                && providerId.equals(existingProviderId)
                && existingOfficialKeyId != null) {
            int affected = modelRepository.updateOfficialKey(
                    existingOfficialKeyId,
                    null,
                    encryptedApiKey,
                    maskedApiKey,
                    null,
                    normalizedStatus
            );
            if (affected != 1) {
                throw BusinessException.of("Official model key not found");
            }
            return new KeyBinding(OFFICIAL_KEY_SOURCE, null, existingOfficialKeyId);
        }

        return resolveKeyBindingForCreate(
                userId,
                businessIdGenerator.nextId(),
                providerId,
                modelName,
                effectiveKeySourceType,
                normalizedApiKey,
                status
        );
    }

    private String normalizeKeySourceType(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw BusinessException.of("Key source type is required");
        }
        String upper = normalized.toUpperCase();
        if (!USER_KEY_SOURCE.equals(upper) && !OFFICIAL_KEY_SOURCE.equals(upper)) {
            throw BusinessException.of("Key source type is invalid");
        }
        return upper;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeApiKey(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw BusinessException.of("Api key is required");
        }
        return normalized;
    }

    private String buildGeneratedKeyName(String modelName) {
        return normalize(modelName) + " Key";
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeNullable(status);
        return normalized == null ? "active" : normalized;
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
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
    private record KeyBinding(String keySourceType, Long userKeyId, Long officialKeyId) {
    }
}
