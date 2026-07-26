package com.penmate.backend.application.rag;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.iam.CapabilityAuthorizationService;
import com.penmate.backend.application.iam.IamPermissionCodes;
import com.penmate.backend.domain.model.model.ModelConfiguration;
import com.penmate.backend.domain.model.model.ModelCredential;
import com.penmate.backend.domain.model.repository.ModelRepository;
import com.penmate.backend.domain.model.service.ModelEndpointPolicy;
import com.penmate.backend.domain.shared.service.SecretCryptoService;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingModelRoutingService {
    private final ModelRepository models;
    private final SecretCryptoService crypto;
    private final ModelEndpointPolicy endpointPolicy;
    private final CapabilityAuthorizationService authorization;

    public EmbeddingModelRoutingService(ModelRepository models, SecretCryptoService crypto,
                                        ModelEndpointPolicy endpointPolicy,
                                        CapabilityAuthorizationService authorization) {
        this.models = models;
        this.crypto = crypto;
        this.endpointPolicy = endpointPolicy;
        this.authorization = authorization;
    }

    public EmbeddingExecutionConfig resolve(Long ownerUserId, Long modelConfigId) {
        ModelConfiguration configuration = modelConfigId == null
                ? null : models.findAccessibleConfiguration(ownerUserId, modelConfigId);
        if (configuration == null || !"EMBEDDING".equals(configuration.getModelType())
                || !"ACTIVE".equalsIgnoreCase(configuration.getStatus())) {
            throw BusinessException.of("Embedding model configuration is unavailable");
        }
        authorization.require(ownerUserId, "SYSTEM".equalsIgnoreCase(configuration.getScopeType())
                ? IamPermissionCodes.MODEL_OFFICIAL_USE
                : IamPermissionCodes.MODEL_USER_USE);
        ModelCredential credential = models.findCredential(configuration);
        String apiKey = "NONE".equalsIgnoreCase(configuration.getProviderAuthType()) ? "" : decrypt(credential);
        String baseUrl = configuration.getBaseUrl() == null || configuration.getBaseUrl().isBlank()
                ? configuration.getProviderBaseUrl() : configuration.getBaseUrl();
        return new EmbeddingExecutionConfig(configuration.getModelConfigId(), configuration.getProviderId(),
                configuration.getProtocolCode(), endpointPolicy.validate(baseUrl, "SYSTEM".equals(configuration.getScopeType())),
                apiKey, configuration.getModelName(), configuration.getDistanceMetric(),
                configuration.getEmbeddingDimensions(),
                "SYSTEM".equals(configuration.getScopeType()));
    }

    private String decrypt(ModelCredential credential) {
        if (credential == null || !"ACTIVE".equalsIgnoreCase(credential.getStatus())
                || credential.getEncryptedApiKey() == null || credential.getEncryptedApiKey().isBlank()) {
            throw BusinessException.of("Embedding model credential is unavailable");
        }
        String value = crypto.decrypt(credential.getEncryptedApiKey());
        if (value == null || value.isBlank()) throw BusinessException.of("Embedding model credential cannot be decrypted");
        return value;
    }

    public record EmbeddingExecutionConfig(Long modelConfigId, Long providerId, String protocolCode,
                                           String baseUrl, String apiKey, String modelName,
                                           String distanceMetric, Integer embeddingDimensions,
                                           boolean systemScope) {
    }
}
