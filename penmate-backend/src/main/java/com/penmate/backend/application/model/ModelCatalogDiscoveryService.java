package com.penmate.backend.application.model;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.model.command.ModelCommands.DiscoverModelsCommand;
import com.penmate.backend.domain.model.model.ModelConfiguration;
import com.penmate.backend.domain.model.model.ModelCredential;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.repository.ModelRepository;
import com.penmate.backend.domain.model.service.ModelCatalogGateway;
import com.penmate.backend.domain.shared.service.SecretCryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ModelCatalogDiscoveryService {

    private final ModelRepository repository;
    private final SecretCryptoService secretCryptoService;
    private final ModelCatalogGateway catalogGateway;

    public DiscoveryResult discover(Long actorUserId, boolean systemScope, DiscoverModelsCommand command) {
        if (actorUserId == null || actorUserId <= 0) throw BusinessException.of("actorUserId is required");
        Objects.requireNonNull(command, "command must not be null");

        ModelConfiguration existing = command.modelConfigId() == null
                ? null : repository.findAccessibleConfiguration(actorUserId, command.modelConfigId());
        if (command.modelConfigId() != null && (existing == null
                || systemScope != "SYSTEM".equals(existing.getScopeType()))) {
            throw BusinessException.notFound("Model configuration not found");
        }

        Long providerId = command.providerId() != null ? command.providerId()
                : existing == null ? null : existing.getProviderId();
        if (providerId == null) throw BusinessException.badRequest("providerId is required");
        ModelProvider provider = repository.findProvider(providerId);
        if (provider == null || !"ACTIVE".equalsIgnoreCase(provider.getStatus())) {
            throw BusinessException.of("Model provider is unavailable");
        }
        if (command.modelType() != null && !command.modelType().isBlank()
                && repository.findCapability(providerId, normalizeModelType(command.modelType())) == null) {
            throw BusinessException.of("Provider does not support " + normalizeModelType(command.modelType()));
        }

        String baseUrl = command.baseUrl() == null || command.baseUrl().isBlank()
                ? existing != null && Objects.equals(existing.getProviderId(), providerId)
                    ? existing.getBaseUrl() : provider.getBaseUrl()
                : command.baseUrl().trim();
        if (baseUrl == null || baseUrl.isBlank()) throw BusinessException.badRequest("baseUrl is required");
        String apiKey = resolveApiKey(provider, existing, providerId, command.apiKey());
        List<String> models = catalogGateway.discover(new ModelCatalogGateway.DiscoveryRequest(
                baseUrl, apiKey, provider.getCode(), provider.getAuthType(), systemScope));
        return new DiscoveryResult(models, models.size());
    }

    private String resolveApiKey(ModelProvider provider, ModelConfiguration existing,
                                 Long providerId, String suppliedApiKey) {
        if ("NONE".equalsIgnoreCase(provider.getAuthType())) return "";
        if (suppliedApiKey != null && !suppliedApiKey.isBlank()) return suppliedApiKey.trim();
        if (existing == null || !Objects.equals(existing.getProviderId(), providerId)) {
            throw BusinessException.badRequest("apiKey is required for this provider");
        }
        ModelCredential credential = repository.findCredential(existing);
        if (credential == null || !"ACTIVE".equalsIgnoreCase(credential.getStatus())
                || credential.getEncryptedApiKey() == null || credential.getEncryptedApiKey().isBlank()) {
            throw BusinessException.of("Model credential is unavailable");
        }
        String apiKey = secretCryptoService.decrypt(credential.getEncryptedApiKey());
        if (apiKey == null || apiKey.isBlank()) throw BusinessException.of("Model credential cannot be decrypted");
        return apiKey;
    }

    private String normalizeModelType(String modelType) {
        String normalized = modelType.trim().toUpperCase(Locale.ROOT);
        if (!"CHAT".equals(normalized) && !"EMBEDDING".equals(normalized)) {
            throw BusinessException.badRequest("modelType is invalid");
        }
        return normalized;
    }

    public record DiscoveryResult(List<String> models, int count) {
    }
}
