package com.penmate.backend.application.model;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.model.command.ModelCommands.CreateConfigurationCommand;
import com.penmate.backend.application.model.command.ModelCommands.SaveUserModelPreferencesCommand;
import com.penmate.backend.application.model.command.ModelCommands.UpdateConfigurationCommand;
import com.penmate.backend.domain.model.model.ModelConfiguration;
import com.penmate.backend.domain.model.model.ModelCredential;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelProviderCapability;
import com.penmate.backend.domain.model.model.ModelUserPreferences;
import com.penmate.backend.domain.model.repository.ModelRepository;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import com.penmate.backend.domain.shared.service.SecretCryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ModelApplicationService {

    private static final int DEFAULT_CONTEXT_WINDOW_TURNS = 6;
    private static final int DEFAULT_MAX_CONTEXT_TOKENS = 128000;

    private final ModelRepository repository;
    private final BusinessIdGenerator idGenerator;
    private final SecretCryptoService secretCryptoService;

    public List<ProviderView> listProviders() {
        return repository.listProviders().stream()
                .map(provider -> new ProviderView(provider, repository.listCapabilities(provider.getProviderId())))
                .toList();
    }

    public List<ModelConfiguration> listAccessibleConfigurations(Long actorUserId) {
        return repository.listAccessibleConfigurations(requireActor(actorUserId));
    }

    public ModelUserPreferences getUserPreferences(Long actorUserId) {
        Long userId = requireActor(actorUserId);
        ModelUserPreferences preferences = repository.findUserPreferences(userId);
        return preferences == null ? defaultPreferences(userId) : preferences;
    }

    @Transactional
    public ModelConfiguration createConfiguration(Long actorUserId,
                                                  boolean systemScope,
                                                  CreateConfigurationCommand command) {
        Long actor = requireActor(actorUserId);
        Objects.requireNonNull(command, "command must not be null");
        String modelType = normalizeModelType(command.modelType());
        ModelProvider provider = requireProvider(command.providerId());
        ModelProviderCapability capability = requireCapability(provider.getProviderId(), modelType);
        String baseUrl = normalizeBaseUrl(command.baseUrl(), provider.getBaseUrl());
        String scope = systemScope ? "SYSTEM" : "USER";

        ModelConfiguration configuration = new ModelConfiguration();
        configuration.setModelConfigId(idGenerator.nextId());
        configuration.setScopeType(scope);
        configuration.setOwnerUserId(systemScope ? null : actor);
        configuration.setProviderId(provider.getProviderId());
        configuration.setProviderCode(provider.getCode());
        configuration.setProtocolCode(capability.getProtocolCode());
        configuration.setDisplayName(requireText(command.displayName(), "displayName"));
        configuration.setModelType(modelType);
        configuration.setModelName(requireText(command.modelName(), "modelName"));
        configuration.setBaseUrl(baseUrl);
        configuration.setDistanceMetric(normalizeMetric(modelType, command.distanceMetric()));
        configuration.setContextWindowTurns(normalizeContextTurns(command.contextWindowTurns()));
        configuration.setMaxContextTokens(normalizeMaxTokens(command.maxContextTokens()));
        configuration.setStatus("ACTIVE");
        configuration.setCreatedBy(actor);
        configuration.setUpdatedBy(actor);
        if (repository.insertConfiguration(configuration) != 1) {
            throw BusinessException.of("Failed to create model configuration");
        }
        createCredentialIfRequired(configuration, provider, command.apiKey());
        return repository.findAccessibleConfiguration(actor, configuration.getModelConfigId());
    }

    public ImpactPreview previewUpdate(Long actorUserId, Long modelConfigId,
                                       boolean systemScope, UpdateConfigurationCommand command) {
        ModelConfiguration existing = repository.findAccessibleConfiguration(requireActor(actorUserId), modelConfigId);
        if (existing == null || systemScope != "SYSTEM".equals(existing.getScopeType())) {
            throw BusinessException.notFound("Model configuration not found");
        }
        ModelConfiguration merged = merge(existing, command, actorUserId);
        boolean embeddingIdentityChange = embeddingIdentityChanged(existing, merged);
        List<Long> projectIds = repository.listDependentProjectIds(modelConfigId);
        return new ImpactPreview(modelConfigId, existing.getModelType(), embeddingIdentityChange,
                projectIds, repository.hasNonterminalRunReference(modelConfigId));
    }

    @Transactional
    public ModelConfiguration updateConfiguration(Long actorUserId,
                                                  Long modelConfigId,
                                                  boolean systemScope,
                                                  UpdateConfigurationCommand command) {
        Long actor = requireActor(actorUserId);
        ModelConfiguration existing = repository.findOwnedConfigurationForUpdate(actor, modelConfigId, systemScope);
        if (existing == null) {
            throw BusinessException.notFound("Model configuration not found");
        }
        if (repository.hasNonterminalRunReference(modelConfigId)) {
            throw BusinessException.conflict("Model configuration is used by a nonterminal Agent Run");
        }
        ModelCredential existingCredential = repository.findCredential(existing);
        ModelConfiguration merged = merge(existing, command, actor);
        boolean embeddingIdentityChange = embeddingIdentityChanged(existing, merged);
        if (embeddingIdentityChange) {
            repository.lockDependentProjectIds(modelConfigId);
        }
        if (repository.updateConfiguration(merged) != 1) {
            throw BusinessException.of("Failed to update model configuration");
        }
        updateCredential(existingCredential, merged, command.apiKey());
        if (embeddingIdentityChange) {
            repository.markDependentProjectsReindexRequired(
                    modelConfigId, "Embedding configuration changed; rebuild the project index");
        }
        return repository.findAccessibleConfiguration(actor, modelConfigId);
    }

    @Transactional
    public int unbindAll(Long actorUserId, Long modelConfigId, boolean systemScope) {
        Long actor = requireActor(actorUserId);
        ModelConfiguration existing = repository.findOwnedConfigurationForUpdate(actor, modelConfigId, systemScope);
        if (existing == null) {
            throw BusinessException.notFound("Model configuration not found");
        }
        List<Long> projects = repository.lockDependentProjectIds(modelConfigId);
        if (repository.hasNonterminalRunReference(modelConfigId)) {
            throw BusinessException.conflict("A dependent project has a nonterminal Agent Run");
        }
        repository.unbindDependentProjects(modelConfigId);
        repository.clearUserDefaultReferences(modelConfigId);
        return projects.size();
    }

    @Transactional
    public void deleteConfiguration(Long actorUserId, Long modelConfigId, boolean systemScope) {
        Long actor = requireActor(actorUserId);
        ModelConfiguration existing = repository.findOwnedConfigurationForUpdate(actor, modelConfigId, systemScope);
        if (existing == null) {
            throw BusinessException.notFound("Model configuration not found");
        }
        if (repository.hasNonterminalRunReference(modelConfigId)) {
            throw BusinessException.conflict("Model configuration is used by a nonterminal Agent Run");
        }
        if (repository.hasAnyReference(modelConfigId)) {
            throw BusinessException.conflict("Unbind projects and user defaults before deleting this model configuration");
        }
        repository.softDeleteCredential(existing);
        if (repository.softDeleteConfiguration(existing, actor) != 1) {
            throw BusinessException.of("Failed to delete model configuration");
        }
    }

    @Transactional
    public ModelUserPreferences saveUserPreferences(Long actorUserId, SaveUserModelPreferencesCommand command) {
        Long actor = requireActor(actorUserId);
        Objects.requireNonNull(command, "command must not be null");
        requireUsable(actor, command.defaultMainChatModelConfigId(), "CHAT", "defaultMainChatModelConfigId");
        requireUsable(actor, command.defaultWorkerChatModelConfigId(), "CHAT", "defaultWorkerChatModelConfigId");
        requireUsable(actor, command.defaultEmbeddingModelConfigId(), "EMBEDDING", "defaultEmbeddingModelConfigId");
        requireUsable(actor, command.defaultRouterModelConfigId(), "CHAT", "defaultRouterModelConfigId");

        ModelUserPreferences preferences = new ModelUserPreferences();
        preferences.setUserId(actor);
        preferences.setDefaultMainChatModelConfigId(command.defaultMainChatModelConfigId());
        preferences.setDefaultWorkerChatModelConfigId(command.defaultWorkerChatModelConfigId());
        preferences.setDefaultEmbeddingModelConfigId(command.defaultEmbeddingModelConfigId());
        preferences.setDefaultRouterModelConfigId(command.defaultRouterModelConfigId());
        String routing = normalizeRoutingMode(command.defaultStoryBibleRoutingMode());
        if (command.defaultEmbeddingModelConfigId() == null && !"LLM_SELECTOR".equals(routing)) {
            throw BusinessException.of("Projects without an Embedding model must default to LLM_SELECTOR");
        }
        preferences.setDefaultStoryBibleRoutingMode(routing);
        int target = valueOrDefault(command.defaultChunkTargetCharacters(), 800);
        int overlap = valueOrDefault(command.defaultChunkOverlapCharacters(), 120);
        int max = valueOrDefault(command.defaultChunkMaxCharacters(), 1200);
        validateChunking(target, overlap, max);
        preferences.setDefaultChunkTargetCharacters(target);
        preferences.setDefaultChunkOverlapCharacters(overlap);
        preferences.setDefaultChunkMaxCharacters(max);
        repository.upsertUserPreferences(preferences);
        return repository.findUserPreferences(actor);
    }

    private ModelConfiguration merge(ModelConfiguration existing, UpdateConfigurationCommand command, Long actor) {
        Objects.requireNonNull(command, "command must not be null");
        Long providerId = command.providerId() == null ? existing.getProviderId() : command.providerId();
        ModelProvider provider = requireProvider(providerId);
        requireCapability(providerId, existing.getModelType());
        ModelConfiguration merged = new ModelConfiguration();
        merged.setModelConfigId(existing.getModelConfigId());
        merged.setScopeType(existing.getScopeType());
        merged.setOwnerUserId(existing.getOwnerUserId());
        merged.setProviderId(providerId);
        merged.setDisplayName(command.displayName() == null
                ? existing.getDisplayName() : requireText(command.displayName(), "displayName"));
        merged.setModelType(existing.getModelType());
        merged.setModelName(command.modelName() == null
                ? existing.getModelName() : requireText(command.modelName(), "modelName"));
        merged.setBaseUrl(command.baseUrl() == null
                ? existing.getBaseUrl() : normalizeBaseUrl(command.baseUrl(), provider.getBaseUrl()));
        merged.setDistanceMetric("EMBEDDING".equals(existing.getModelType())
                ? normalizeMetric("EMBEDDING", command.distanceMetric() == null
                    ? existing.getDistanceMetric() : command.distanceMetric())
                : null);
        merged.setContextWindowTurns(command.contextWindowTurns() == null
                ? existing.getContextWindowTurns() : normalizeContextTurns(command.contextWindowTurns()));
        merged.setMaxContextTokens(command.maxContextTokens() == null
                ? existing.getMaxContextTokens() : normalizeMaxTokens(command.maxContextTokens()));
        merged.setStatus(command.status() == null ? existing.getStatus() : normalizeStatus(command.status()));
        merged.setCreatedBy(existing.getCreatedBy());
        merged.setUpdatedBy(actor);
        return merged;
    }

    private void createCredentialIfRequired(ModelConfiguration configuration, ModelProvider provider, String apiKey) {
        if ("NONE".equalsIgnoreCase(provider.getAuthType())) {
            return;
        }
        String secret = requireText(apiKey, "apiKey");
        ModelCredential credential = credential(configuration, idGenerator.nextId(), secret);
        if (repository.insertCredential(configuration, credential) != 1) {
            throw BusinessException.of("Failed to store model credential");
        }
    }

    private void updateCredential(ModelCredential existing, ModelConfiguration configuration, String apiKey) {
        ModelProvider provider = requireProvider(configuration.getProviderId());
        if ("NONE".equalsIgnoreCase(provider.getAuthType())) {
            if (existing != null) repository.softDeleteCredential(configuration);
            return;
        }
        if (apiKey == null || apiKey.isBlank()) {
            if (existing == null) throw BusinessException.of("apiKey is required for this provider");
            existing.setProviderId(configuration.getProviderId());
            existing.setStatus("ACTIVE");
            repository.updateCredential(configuration, existing);
            return;
        }
        ModelCredential credential = credential(configuration,
                existing == null ? idGenerator.nextId() : existing.getCredentialId(), apiKey);
        int affected = existing == null
                ? repository.insertCredential(configuration, credential)
                : repository.updateCredential(configuration, credential);
        if (affected != 1) throw BusinessException.of("Failed to update model credential");
    }

    private ModelCredential credential(ModelConfiguration configuration, Long credentialId, String apiKey) {
        ModelCredential credential = new ModelCredential();
        credential.setCredentialId(credentialId);
        credential.setModelConfigId(configuration.getModelConfigId());
        credential.setOwnerUserId(configuration.getOwnerUserId());
        credential.setProviderId(configuration.getProviderId());
        credential.setEncryptedApiKey(secretCryptoService.encrypt(apiKey));
        credential.setMaskedApiKey(mask(apiKey));
        credential.setStatus("ACTIVE");
        return credential;
    }

    private ModelProvider requireProvider(Long providerId) {
        if (providerId == null) throw BusinessException.of("providerId is required");
        ModelProvider provider = repository.findProvider(providerId);
        if (provider == null || !"ACTIVE".equalsIgnoreCase(provider.getStatus())) {
            throw BusinessException.of("Model provider is unavailable");
        }
        return provider;
    }

    private ModelProviderCapability requireCapability(Long providerId, String modelType) {
        ModelProviderCapability capability = repository.findCapability(providerId, modelType);
        if (capability == null) throw BusinessException.of("Provider does not support " + modelType);
        if ("EMBEDDING".equals(modelType) && !"OPENAI_EMBEDDINGS".equals(capability.getProtocolCode())) {
            throw BusinessException.of("Embedding protocol is not implemented: " + capability.getProtocolCode());
        }
        return capability;
    }

    private void requireUsable(Long actor, Long configId, String modelType, String field) {
        if (configId != null && !repository.existsAccessibleActiveConfiguration(actor, configId, modelType)) {
            throw BusinessException.of(field + " is unavailable");
        }
    }

    private boolean embeddingIdentityChanged(ModelConfiguration left, ModelConfiguration right) {
        return "EMBEDDING".equals(left.getModelType()) && (
                !Objects.equals(left.getProviderId(), right.getProviderId())
                || !Objects.equals(normalized(left.getBaseUrl()), normalized(right.getBaseUrl()))
                || !Objects.equals(left.getModelName(), right.getModelName())
                || !Objects.equals(left.getDistanceMetric(), right.getDistanceMetric()));
    }

    private String normalizeBaseUrl(String requested, String providerDefault) {
        String value = requested == null || requested.isBlank() ? providerDefault : requested.trim();
        if (value == null || value.isBlank()) throw BusinessException.of("baseUrl is required");
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw BusinessException.of("baseUrl must be an HTTP(S) URL without user info");
            }
            return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        } catch (URISyntaxException exception) {
            throw BusinessException.of("baseUrl is invalid");
        }
    }

    private String normalizeModelType(String value) {
        String type = requireText(value, "modelType").toUpperCase(Locale.ROOT);
        if (!List.of("CHAT", "EMBEDDING").contains(type)) throw BusinessException.of("Unsupported modelType");
        return type;
    }

    private String normalizeMetric(String modelType, String value) {
        if ("CHAT".equals(modelType)) return null;
        String metric = value == null || value.isBlank() ? "COSINE" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("COSINE", "INNER_PRODUCT", "L2").contains(metric)) {
            throw BusinessException.of("Unsupported distanceMetric");
        }
        return metric;
    }

    private String normalizeRoutingMode(String value) {
        String mode = value == null || value.isBlank() ? "LLM_SELECTOR" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("RETRIEVAL", "LLM_SELECTOR", "RETRIEVAL_THEN_LLM").contains(mode)) {
            throw BusinessException.of("Unsupported Story Bible routing mode");
        }
        return mode;
    }

    private String normalizeStatus(String value) {
        String status = value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ACTIVE", "DISABLED").contains(status)) throw BusinessException.of("Unsupported status");
        return status;
    }

    private int normalizeContextTurns(Integer value) {
        int result = value == null ? DEFAULT_CONTEXT_WINDOW_TURNS : value;
        if (result < 0 || result > 100) throw BusinessException.of("contextWindowTurns must be between 0 and 100");
        return result;
    }

    private int normalizeMaxTokens(Integer value) {
        int result = value == null ? DEFAULT_MAX_CONTEXT_TOKENS : value;
        if (result <= 0) throw BusinessException.of("maxContextTokens must be positive");
        return result;
    }

    private void validateChunking(int target, int overlap, int max) {
        if (target <= 0 || overlap < 0 || overlap >= target || max < target) {
            throw BusinessException.of("Invalid chunk target, overlap, or maximum");
        }
    }

    private ModelUserPreferences defaultPreferences(Long userId) {
        ModelUserPreferences result = new ModelUserPreferences();
        result.setUserId(userId);
        result.setDefaultStoryBibleRoutingMode("LLM_SELECTOR");
        result.setDefaultChunkTargetCharacters(800);
        result.setDefaultChunkOverlapCharacters(120);
        result.setDefaultChunkMaxCharacters(1200);
        return result;
    }

    private Long requireActor(Long actorUserId) {
        return Objects.requireNonNull(actorUserId, "actorUserId must not be null");
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw BusinessException.of(field + " is required");
        return value.trim();
    }

    private int valueOrDefault(Integer value, int defaultValue) { return value == null ? defaultValue : value; }
    private String normalized(String value) { return value == null ? null : value.trim().toLowerCase(Locale.ROOT); }
    private String mask(String key) {
        String normalized = requireText(key, "apiKey");
        int keep = Math.min(4, normalized.length());
        return "****" + normalized.substring(normalized.length() - keep);
    }

    public record ProviderView(ModelProvider provider, List<ModelProviderCapability> capabilities) {
        public ProviderView { capabilities = List.copyOf(capabilities); }
    }

    public record ImpactPreview(Long modelConfigId, String modelType, boolean embeddingIdentityChange,
                                List<Long> projectIds, boolean blockedByRun) {
        public ImpactPreview { projectIds = List.copyOf(projectIds); }
    }
}
