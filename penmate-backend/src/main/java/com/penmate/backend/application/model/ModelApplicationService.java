package com.penmate.backend.application.model;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.model.command.ModelCommands.CreateConfigurationCommand;
import com.penmate.backend.application.model.command.ModelCommands.SaveUserModelPreferencesCommand;
import com.penmate.backend.application.model.command.ModelCommands.UpdateConfigurationCommand;
import com.penmate.backend.application.model.command.ModelCommands.ProbeEmbeddingDimensionCommand;
import com.penmate.backend.domain.model.model.ModelConfiguration;
import com.penmate.backend.domain.model.model.ModelCredential;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelProviderCapability;
import com.penmate.backend.domain.model.model.ModelUserPreferences;
import com.penmate.backend.domain.model.repository.ModelRepository;
import com.penmate.backend.domain.model.service.ModelCatalogGateway;
import com.penmate.backend.domain.model.service.ModelEndpointPolicy;
import com.penmate.backend.domain.rag.service.EmbeddingDimensionProbeGateway;
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
    private static final int DEFAULT_MAX_CONTEXT_TOKENS = ModelCapabilityCatalogService.FALLBACK_CONTEXT_TOKENS;

    private final ModelRepository repository;
    private final BusinessIdGenerator idGenerator;
    private final SecretCryptoService secretCryptoService;
    private final ModelEndpointPolicy modelEndpointPolicy;
    private final EmbeddingDimensionProbeGateway embeddingDimensionProbeGateway;
    private final ModelCapabilityCatalogService capabilityCatalog;
    private final ModelCatalogGateway modelCatalogGateway;

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

    public EmbeddingDimensionProbeResult probeEmbeddingDimensions(Long actorUserId, boolean systemScope,
                                                                  ProbeEmbeddingDimensionCommand command) {
        Long actor = requireActor(actorUserId);
        Objects.requireNonNull(command, "command must not be null");
        ModelConfiguration existing = command.modelConfigId() == null
                ? null : repository.findAccessibleConfiguration(actor, command.modelConfigId());
        if (command.modelConfigId() != null && (existing == null
                || systemScope != "SYSTEM".equals(existing.getScopeType()))) {
            throw BusinessException.notFound("Model configuration not found");
        }
        if (existing != null && !"EMBEDDING".equals(existing.getModelType())) {
            throw BusinessException.of("Only Embedding model configurations can be probed");
        }

        Long providerId = command.providerId() != null ? command.providerId()
                : existing == null ? null : existing.getProviderId();
        ModelProvider provider = requireProvider(providerId);
        requireCapability(providerId, "EMBEDDING");
        String modelName = command.modelName() == null || command.modelName().isBlank()
                ? existing == null ? null : existing.getModelName()
                : command.modelName();
        modelName = requireText(modelName, "modelName");
        String requestedBaseUrl;
        if (command.baseUrl() != null) {
            requestedBaseUrl = normalizeBaseUrl(command.baseUrl(), provider.getBaseUrl());
        } else if (existing != null && Objects.equals(existing.getProviderId(), providerId)) {
            requestedBaseUrl = existing.getBaseUrl();
        } else {
            requestedBaseUrl = provider.getBaseUrl();
        }
        String baseUrl = validateEndpoint(normalizeBaseUrl(requestedBaseUrl, provider.getBaseUrl()), systemScope);
        String apiKey = probeApiKey(provider, existing, providerId, command.apiKey());
        Integer requestedDimensions = normalizeEmbeddingDimensions("EMBEDDING", command.embeddingDimensions());
        int actualDimensions = embeddingDimensionProbeGateway.probe(
                new EmbeddingDimensionProbeGateway.ProbeRequest(baseUrl, apiKey, modelName,
                        systemScope, requestedDimensions));
        return new EmbeddingDimensionProbeResult(actualDimensions, requestedDimensions,
                requestedDimensions == null);
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
        String baseUrl = validateEndpoint(normalizeBaseUrl(command.baseUrl(), provider.getBaseUrl()), systemScope);
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
        configuration.setEmbeddingDimensions(normalizeEmbeddingDimensions(modelType, command.embeddingDimensions()));
        configuration.setContextWindowTurns(normalizeContextTurns(command.contextWindowTurns()));
        ModelCapabilityCatalogService.Resolution capacity = resolveCapacity(provider, configuration.getModelName(),
                configuration.getBaseUrl(), command.apiKey(), systemScope, modelType,
                command.maxContextTokens(), command.maxOutputTokens());
        configuration.setMaxContextTokens(capacity.maxContextTokens());
        configuration.setMaxOutputTokens(capacity.maxOutputTokens());
        configuration.setContextCapacitySource(capacity.source());
        configuration.setContextCapacitySourceUrl(capacity.sourceUrl());
        configuration.setContextCapacityVerifiedAt(capacity.verifiedAt());
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
        requireUsable(actor, command.defaultCreativeModelConfigId(), "CHAT", "defaultCreativeModelConfigId");
        requireUsable(actor, command.defaultContextSelectorModelConfigId(), "CHAT", "defaultContextSelectorModelConfigId");
        requireUsable(actor, command.defaultEmbeddingModelConfigId(), "EMBEDDING", "defaultEmbeddingModelConfigId");

        ModelUserPreferences preferences = new ModelUserPreferences();
        preferences.setUserId(actor);
        preferences.setDefaultCreativeModelConfigId(command.defaultCreativeModelConfigId());
        preferences.setDefaultContextSelectorModelConfigId(command.defaultContextSelectorModelConfigId());
        preferences.setDefaultEmbeddingModelConfigId(command.defaultEmbeddingModelConfigId());
        String routing = normalizeRoutingMode(command.defaultStoryBibleRoutingMode());
        if (command.defaultEmbeddingModelConfigId() == null && requiresEmbedding(routing)) {
            throw BusinessException.of("Retrieval routing requires an Embedding model");
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
        boolean modelIdentityChanged = !Objects.equals(providerId, existing.getProviderId())
                || (command.modelName() != null && !Objects.equals(
                existing.getModelName(), command.modelName().trim()))
                || (command.baseUrl() != null && !Objects.equals(
                normalized(existing.getBaseUrl()), normalized(command.baseUrl())));
        merged.setDisplayName(command.displayName() == null
                ? existing.getDisplayName() : requireText(command.displayName(), "displayName"));
        merged.setModelType(existing.getModelType());
        merged.setModelName(command.modelName() == null
                ? existing.getModelName() : requireText(command.modelName(), "modelName"));
        String candidateBaseUrl = command.baseUrl() == null
                ? existing.getBaseUrl() : normalizeBaseUrl(command.baseUrl(), provider.getBaseUrl());
        merged.setBaseUrl(validateEndpoint(candidateBaseUrl, "SYSTEM".equals(existing.getScopeType())));
        merged.setDistanceMetric("EMBEDDING".equals(existing.getModelType())
                ? normalizeMetric("EMBEDDING", command.distanceMetric() == null
                    ? existing.getDistanceMetric() : command.distanceMetric())
                : null);
        merged.setEmbeddingDimensions("EMBEDDING".equals(existing.getModelType())
                ? (command.embeddingDimensionsSet()
                    ? normalizeEmbeddingDimensions("EMBEDDING", command.embeddingDimensions())
                    : existing.getEmbeddingDimensions())
                : null);
        merged.setContextWindowTurns(command.contextWindowTurns() == null
                ? existing.getContextWindowTurns() : normalizeContextTurns(command.contextWindowTurns()));
        boolean autoDetectCapacity = Boolean.TRUE.equals(command.autoDetectCapacity());
        boolean manualCapacity = !autoDetectCapacity && (command.maxContextTokens() != null
                || "MANUAL".equalsIgnoreCase(existing.getContextCapacitySource()));
        String capacityApiKey = capacityProbeApiKey(provider, existing, providerId, command.apiKey());
        if (command.maxContextTokens() != null && !autoDetectCapacity) {
            ModelCapabilityCatalogService.Resolution capacity = resolveCapacity(provider, merged.getModelName(),
                    merged.getBaseUrl(), capacityApiKey, "SYSTEM".equals(existing.getScopeType()), existing.getModelType(),
                    command.maxContextTokens(), command.maxOutputTokens());
            merged.setMaxContextTokens(capacity.maxContextTokens());
            merged.setMaxOutputTokens(capacity.maxOutputTokens());
            merged.setContextCapacitySource(capacity.source());
            merged.setContextCapacitySourceUrl(capacity.sourceUrl());
            merged.setContextCapacityVerifiedAt(capacity.verifiedAt());
        } else if (autoDetectCapacity || (modelIdentityChanged && !manualCapacity)) {
            ModelCapabilityCatalogService.Resolution capacity = resolveCapacity(provider, merged.getModelName(),
                    merged.getBaseUrl(), capacityApiKey, "SYSTEM".equals(existing.getScopeType()), existing.getModelType(),
                    null, command.maxOutputTokens());
            merged.setMaxContextTokens(capacity.maxContextTokens());
            merged.setMaxOutputTokens(capacity.maxOutputTokens());
            merged.setContextCapacitySource(capacity.source());
            merged.setContextCapacitySourceUrl(capacity.sourceUrl());
            merged.setContextCapacityVerifiedAt(capacity.verifiedAt());
        } else {
            merged.setMaxContextTokens(existing.getMaxContextTokens());
            merged.setMaxOutputTokens(command.maxOutputTokens() == null
                    ? normalizeMaxOutputTokens(existing.getMaxOutputTokens())
                    : normalizeMaxOutputTokens(command.maxOutputTokens()));
            merged.setContextCapacitySource(existing.getContextCapacitySource());
            merged.setContextCapacitySourceUrl(existing.getContextCapacitySourceUrl());
            merged.setContextCapacityVerifiedAt(existing.getContextCapacityVerifiedAt());
        }
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

    private String probeApiKey(ModelProvider provider, ModelConfiguration existing, Long providerId, String apiKey) {
        if ("NONE".equalsIgnoreCase(provider.getAuthType())) return "";
        if (apiKey != null && !apiKey.isBlank()) return apiKey.trim();
        if (existing != null && Objects.equals(existing.getProviderId(), providerId)) {
            return decryptCredential(repository.findCredential(existing));
        }
        throw BusinessException.of("apiKey is required for this provider");
    }

    private String decryptCredential(ModelCredential credential) {
        if (credential == null || !"ACTIVE".equalsIgnoreCase(credential.getStatus())
                || credential.getEncryptedApiKey() == null || credential.getEncryptedApiKey().isBlank()) {
            throw BusinessException.of("Model credential is unavailable");
        }
        String value = secretCryptoService.decrypt(credential.getEncryptedApiKey());
        if (value == null || value.isBlank()) throw BusinessException.of("Model credential cannot be decrypted");
        return value;
    }

    private String capacityProbeApiKey(ModelProvider provider, ModelConfiguration existing,
                                       Long providerId, String suppliedApiKey) {
        if ("NONE".equalsIgnoreCase(provider.getAuthType())) return "";
        if (suppliedApiKey != null && !suppliedApiKey.isBlank()) return suppliedApiKey.trim();
        if (existing == null || !Objects.equals(existing.getProviderId(), providerId)) return "";
        try {
            return decryptCredential(repository.findCredential(existing));
        } catch (RuntimeException exception) {
            return "";
        }
    }

    private String validateEndpoint(String baseUrl, boolean systemScope) {
        // Unit tests that construct this service with legacy Mockito fixtures do not provide the new policy.
        return modelEndpointPolicy == null ? baseUrl : modelEndpointPolicy.validate(baseUrl, systemScope);
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
                || !Objects.equals(left.getDistanceMetric(), right.getDistanceMetric())
                || !Objects.equals(left.getEmbeddingDimensions(), right.getEmbeddingDimensions()));
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

    private Integer normalizeEmbeddingDimensions(String modelType, Integer value) {
        if (!"EMBEDDING".equals(modelType)) return null;
        if (value == null) return null;
        if (value < 1 || value > 4000) {
            throw BusinessException.of("embeddingDimensions must be between 1 and 4000");
        }
        return value;
    }

    private String normalizeRoutingMode(String value) {
        String mode = value == null || value.isBlank() ? "AGENT_DRIVEN" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("AGENT_DRIVEN", "RETRIEVAL", "LLM_SELECTOR", "RETRIEVAL_THEN_LLM").contains(mode)) {
            throw BusinessException.of("Unsupported Story Bible routing mode");
        }
        return mode;
    }

    private boolean requiresEmbedding(String mode) {
        return "RETRIEVAL".equals(mode) || "RETRIEVAL_THEN_LLM".equals(mode);
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

    private ModelCapabilityCatalogService.Resolution resolveCapacity(ModelProvider provider, String modelName,
                                                                     String baseUrl, String apiKey,
                                                                     boolean systemScope, String modelType,
                                                                     Integer requestedContext, Integer requestedOutput) {
        String providerCode = provider == null ? null : provider.getCode();
        if (requestedContext != null) {
            if (capabilityCatalog != null) {
                return capabilityCatalog.resolveForSave(providerCode, modelName, requestedContext, requestedOutput);
            }
            int context = normalizeMaxTokens(requestedContext);
            int output = normalizeMaxOutputTokens(requestedOutput);
            return new ModelCapabilityCatalogService.Resolution(context, output, "MANUAL", null, null);
        }
        if ("CHAT".equals(modelType) && modelCatalogGateway != null && provider != null
                && ("NONE".equalsIgnoreCase(provider.getAuthType()) || (apiKey != null && !apiKey.isBlank()))) {
            try {
                var probed = modelCatalogGateway.probeCapacity(new ModelCatalogGateway.DiscoveryRequest(
                        baseUrl, apiKey, providerCode, provider.getAuthType(), systemScope), modelName);
                if (probed.isPresent()) {
                    ModelCatalogGateway.ModelCapacity capacity = probed.get();
                    int output = requestedOutput != null ? normalizeMaxOutputTokens(requestedOutput)
                            : capacity.maxOutputTokens() != null ? capacity.maxOutputTokens()
                            : capabilityCatalog == null
                                ? ModelCapabilityCatalogService.FALLBACK_OUTPUT_TOKENS
                                : capabilityCatalog.resolveForSave(providerCode, modelName, null, null).maxOutputTokens();
                    return new ModelCapabilityCatalogService.Resolution(capacity.maxContextTokens(), output,
                            "PROVIDER", capacity.sourceUrl(), capacity.verifiedAt());
                }
            } catch (RuntimeException ignored) {
                // Capability probing is best effort; model configuration saving must still work.
            }
        }
        if (capabilityCatalog != null) {
            return capabilityCatalog.resolveForSave(providerCode, modelName, null, requestedOutput);
        }
        int context = normalizeMaxTokens(requestedContext);
        int output = normalizeMaxOutputTokens(requestedOutput);
        return new ModelCapabilityCatalogService.Resolution(context, output, "FALLBACK", null, null);
    }

    private int normalizeMaxOutputTokens(Integer value) {
        int result = value == null ? 8_192 : value;
        if (result <= 0) throw BusinessException.of("maxOutputTokens must be positive");
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
        result.setDefaultStoryBibleRoutingMode("AGENT_DRIVEN");
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

    public record EmbeddingDimensionProbeResult(int dimensions, Integer requestedDimensions, boolean nativeMode) {
    }

    public record ImpactPreview(Long modelConfigId, String modelType, boolean embeddingIdentityChange,
                                List<Long> projectIds, boolean blockedByRun) {
        public ImpactPreview { projectIds = List.copyOf(projectIds); }
    }
}
