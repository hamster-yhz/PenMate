package com.penmate.backend.application.agent;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.model.BuiltinModelProviders;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.repository.ModelRepository;
import com.penmate.backend.domain.shared.service.SecretCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Agent 模型路由与执行配置解析服务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentModelRoutingService {

    private final ModelRepository modelRepository;
    private final SecretCryptoService secretCryptoService;

    public AgentLlmExecutionConfig resolveExecutionConfig(Long userId, Long modelConfigId, String traceId) {
        Map<String, Object> config = modelRepository.findUserModelConfig(userId, modelConfigId);
        if (config == null) {
            throw BusinessException.of("Model config not found");
        }

        String resolvedModelName = stringValue(config.get("modelName"));
        if (resolvedModelName == null || resolvedModelName.isBlank()) {
            throw BusinessException.of("Model name is required");
        }

        Long resolvedProviderId = longValue(config.get("providerId"));
        if (resolvedProviderId == null) {
            throw BusinessException.of("Model provider cannot be resolved");
        }

        ModelProvider provider = BuiltinModelProviders.findById(resolvedProviderId).orElse(null);
        if (provider == null || isInactive(provider.getStatus())) {
            log.warn("用户模型配置引用的厂商不可用: userId={}, modelConfigId={}, providerId={}, traceId={}",
                    userId,
                    modelConfigId,
                    resolvedProviderId,
                    traceId);
            throw BusinessException.of("Model provider is unavailable");
        }

        String keyStatus = stringValue(config.get("keyStatus"));
        if (keyStatus == null || !"active".equalsIgnoreCase(keyStatus.trim())) {
            throw BusinessException.of("Model config key is unavailable");
        }

        String encryptedApiKey = stringValue(config.get("encryptedApiKey"));
        if (encryptedApiKey == null || encryptedApiKey.isBlank()) {
            log.warn("用户模型配置缺少可用密钥: userId={}, modelConfigId={}, traceId={}", userId, modelConfigId, traceId);
            throw BusinessException.of("Model config key is required");
        }
        String plainApiKey = secretCryptoService.decrypt(encryptedApiKey);
        if (plainApiKey == null || plainApiKey.isBlank()) {
            log.warn("用户模型配置密钥解密后为空: userId={}, modelConfigId={}, traceId={}", userId, modelConfigId, traceId);
            throw BusinessException.of("Model config key decrypt failed");
        }

        String resolvedBaseUrl = stringValue(config.get("baseUrl"));
        if (resolvedBaseUrl == null || resolvedBaseUrl.isBlank()) {
            resolvedBaseUrl = provider.getBaseUrl();
        }
        Integer contextWindowTurns = intValue(config.get("contextWindowTurns"), 6);
        return AgentLlmExecutionConfig.builder()
                .modelConfigId(modelConfigId)
                .providerCode(provider.getCode())
                .baseUrl(resolvedBaseUrl)
                .apiKey(plainApiKey)
                .modelName(resolvedModelName.trim())
                .keySource("MODEL_CONFIG")
                .contextWindowTurns(contextWindowTurns)
                .build();
    }

    private boolean isInactive(String status) {
        return status != null && "disabled".equalsIgnoreCase(status.trim());
    }

    private Integer intValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
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
}
