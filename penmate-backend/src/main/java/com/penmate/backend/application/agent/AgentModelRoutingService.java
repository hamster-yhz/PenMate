package com.penmate.backend.application.agent;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentReasoningPolicy;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.iam.CapabilityAuthorizationService;
import com.penmate.backend.application.iam.IamPermissionCodes;
import com.penmate.backend.domain.model.model.ModelConfiguration;
import com.penmate.backend.domain.model.model.ModelCredential;
import com.penmate.backend.domain.model.model.ModelUserPreferences;
import com.penmate.backend.domain.model.repository.ModelRepository;
import com.penmate.backend.domain.shared.service.SecretCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentModelRoutingService {

    private final ModelRepository modelRepository;
    private final SecretCryptoService secretCryptoService;
    private final CapabilityAuthorizationService authorization;

    public AgentLlmExecutionConfig resolveExecutionConfig(Long userId, Long modelConfigId, String traceId) {
        ResolvedModel resolved = resolveModel(userId, modelConfigId);
        Long resolvedModelConfigId = resolved.modelConfigId();
        ModelConfiguration config = resolved.configuration();
        ModelCredential credential = modelRepository.findCredential(config);
        String apiKey = "NONE".equalsIgnoreCase(config.getProviderAuthType()) ? "" : decrypt(credential, traceId);
        String baseUrl = config.getBaseUrl() == null || config.getBaseUrl().isBlank()
                ? config.getProviderBaseUrl() : config.getBaseUrl();
        return AgentLlmExecutionConfig.builder()
                .modelConfigId(resolvedModelConfigId)
                .providerCode(config.getProviderCode())
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(config.getModelName())
                .keySource("MODEL_CONFIG")
                .contextWindowTurns(config.getContextWindowTurns() == null ? 6 : config.getContextWindowTurns())
                .maxContextTokens(config.getMaxContextTokens())
                .maxOutputTokens(config.getMaxOutputTokens())
                .reasoningPolicy(reasoningPolicy(config))
                .protocolCode(config.getProtocolCode())
                .build();
    }

    public ModelExecutionSnapshot resolveSnapshot(Long userId, Long modelConfigId) {
        ResolvedModel resolved = resolveModel(userId, modelConfigId);
        return new ModelExecutionSnapshot(resolved.modelConfigId(), reasoningPolicy(resolved.configuration()));
    }

    private ResolvedModel resolveModel(Long userId, Long modelConfigId) {
        Long resolvedModelConfigId = modelConfigId;
        if (resolvedModelConfigId == null) {
            ModelUserPreferences preferences = modelRepository.findUserPreferences(userId);
            resolvedModelConfigId = preferences == null ? null : preferences.getDefaultCreativeModelConfigId();
        }
        if (resolvedModelConfigId == null) {
            throw BusinessException.of("Default creative model is not configured");
        }
        ModelConfiguration config = modelRepository.findAccessibleConfiguration(userId, resolvedModelConfigId);
        if (config == null) {
            throw BusinessException.of("Model config not found");
        }
        if (!"CHAT".equals(config.getModelType()) || !"ACTIVE".equalsIgnoreCase(config.getStatus())) {
            throw BusinessException.of("Chat model configuration is unavailable");
        }
        authorization.require(userId, "SYSTEM".equalsIgnoreCase(config.getScopeType())
                ? IamPermissionCodes.MODEL_OFFICIAL_USE
                : IamPermissionCodes.MODEL_USER_USE);
        return new ResolvedModel(resolvedModelConfigId, config);
    }

    private AgentReasoningPolicy reasoningPolicy(ModelConfiguration config) {
        return new AgentReasoningPolicy(
                config.getReasoningEffort(), config.getReasoningSummary(), config.getReasoningMode());
    }

    private String decrypt(ModelCredential credential, String traceId) {
        if (credential == null || !"ACTIVE".equalsIgnoreCase(credential.getStatus())
                || credential.getEncryptedApiKey() == null || credential.getEncryptedApiKey().isBlank()) {
            throw BusinessException.of("Model config key is unavailable");
        }
        String plain = secretCryptoService.decrypt(credential.getEncryptedApiKey());
        if (plain == null || plain.isBlank()) {
            log.warn("Model credential decrypt failed: modelConfigId={}, traceId={}",
                    credential.getModelConfigId(), traceId);
            throw BusinessException.of("Model config key decrypt failed");
        }
        return plain;
    }

    public record ModelExecutionSnapshot(Long modelConfigId, AgentReasoningPolicy reasoningPolicy) {}

    private record ResolvedModel(Long modelConfigId, ModelConfiguration configuration) {}
}
