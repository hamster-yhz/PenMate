package com.penmate.backend.interfaces.api.model;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.model.ModelApplicationService;
import com.penmate.backend.application.model.ModelCatalogDiscoveryService;
import com.penmate.backend.application.iam.CapabilityAuthorizationService;
import com.penmate.backend.application.iam.IamPermissionCodes;
import com.penmate.backend.application.model.ModelConnectionTestService;
import com.penmate.backend.application.model.command.ModelCommands;
import com.penmate.backend.application.ratelimit.RateLimitAction;
import com.penmate.backend.application.ratelimit.RateLimitApplicationService;
import com.penmate.backend.domain.model.model.ModelConfiguration;
import com.penmate.backend.domain.model.model.ModelProviderCapability;
import com.penmate.backend.domain.model.model.ModelUserPreferences;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import com.penmate.backend.interfaces.api.model.dto.CreateModelConfigurationDto;
import com.penmate.backend.interfaces.api.model.dto.DiscoverModelsDto;
import com.penmate.backend.interfaces.api.model.dto.ProbeEmbeddingDimensionDto;
import com.penmate.backend.interfaces.api.model.dto.SaveModelPreferencesDto;
import com.penmate.backend.interfaces.api.model.dto.UpdateModelConfigurationDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/model")
@RequiredArgsConstructor
public class ModelController {

    private final ModelApplicationService service;
    private final ModelCatalogDiscoveryService catalogDiscovery;
    private final ModelConnectionTestService connectionTests;
    private final RateLimitApplicationService rateLimits;
    private final CapabilityAuthorizationService authorization;

    @GetMapping("/providers")
    public ApiResponse<List<Map<String, Object>>> listProviders(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(service.listProviders().stream().map(this::providerView).toList(), traceId);
    }

    @GetMapping("/configurations")
    public ApiResponse<List<Map<String, Object>>> listConfigurations(
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Long actor = actor(authentication);
        Set<String> permissions = authorization.currentSnapshot(actor).permissions();
        return ApiResponse.success(service.listAccessibleConfigurations(actor).stream()
                .map(configuration -> configurationView(configuration, permissions)).toList(), traceId);
    }

    @PostMapping("/configurations")
    public ApiResponse<Map<String, Object>> createUserConfiguration(
            Authentication authentication,
            @Valid @RequestBody CreateModelConfigurationDto dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Long actor = actor(authentication);
        return ApiResponse.success(configurationView(service.createConfiguration(
                actor, false, createCommand(dto)), authorization.currentSnapshot(actor).permissions()), traceId);
    }

    @PostMapping("/embedding-dimension-probes")
    public ApiResponse<Map<String, Object>> probeUserEmbeddingDimensions(
            Authentication authentication,
            @Valid @RequestBody ProbeEmbeddingDimensionDto dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        rateLimits.consume(RateLimitAction.EMBEDDING_DIMENSION_PROBE, actor(authentication).toString());
        return ApiResponse.success(probeView(service.probeEmbeddingDimensions(
                actor(authentication), false, probeCommand(dto))), traceId);
    }

    @PostMapping("/model-discoveries")
    public ApiResponse<ModelCatalogDiscoveryService.DiscoveryResult> discoverUserModels(
            Authentication authentication,
            @Valid @RequestBody DiscoverModelsDto dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Long actor = actor(authentication);
        rateLimits.consume(RateLimitAction.MODEL_CATALOG_DISCOVERY, actor.toString());
        return ApiResponse.success(catalogDiscovery.discover(actor, false, discoveryCommand(dto)), traceId);
    }

    @PostMapping("/system-model-discoveries")
    @PreAuthorize("hasAuthority('model:system:write')")
    public ApiResponse<ModelCatalogDiscoveryService.DiscoveryResult> discoverSystemModels(
            Authentication authentication,
            @Valid @RequestBody DiscoverModelsDto dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Long actor = actor(authentication);
        rateLimits.consume(RateLimitAction.MODEL_CATALOG_DISCOVERY, actor.toString());
        return ApiResponse.success(catalogDiscovery.discover(actor, true, discoveryCommand(dto)), traceId);
    }

    @PostMapping("/system-embedding-dimension-probes")
    @PreAuthorize("hasAuthority('model:system:write')")
    public ApiResponse<Map<String, Object>> probeSystemEmbeddingDimensions(
            Authentication authentication,
            @Valid @RequestBody ProbeEmbeddingDimensionDto dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        rateLimits.consume(RateLimitAction.EMBEDDING_DIMENSION_PROBE, actor(authentication).toString());
        return ApiResponse.success(probeView(service.probeEmbeddingDimensions(
                actor(authentication), true, probeCommand(dto))), traceId);
    }

    @PostMapping("/configurations/{modelConfigId}/connection-tests")
    public ApiResponse<ModelConnectionTestService.ConnectionTestResult> testUserConnection(
            Authentication authentication,
            @PathVariable String modelConfigId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Long actor = actor(authentication);
        rateLimits.consume(RateLimitAction.MODEL_CONNECTION_TEST, actor.toString());
        return ApiResponse.success(connectionTests.test(actor, id(modelConfigId, "modelConfigId"), false, traceId), traceId);
    }

    @PostMapping("/system-configurations/{modelConfigId}/connection-tests")
    @PreAuthorize("hasAuthority('model:system:write')")
    public ApiResponse<ModelConnectionTestService.ConnectionTestResult> testSystemConnection(
            Authentication authentication,
            @PathVariable String modelConfigId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Long actor = actor(authentication);
        rateLimits.consume(RateLimitAction.MODEL_CONNECTION_TEST, actor.toString());
        return ApiResponse.success(connectionTests.test(actor, id(modelConfigId, "modelConfigId"), true, traceId), traceId);
    }

    @PostMapping("/system-configurations")
    @PreAuthorize("hasAuthority('model:system:write')")
    public ApiResponse<Map<String, Object>> createSystemConfiguration(
            Authentication authentication,
            @Valid @RequestBody CreateModelConfigurationDto dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Long actor = actor(authentication);
        return ApiResponse.success(configurationView(service.createConfiguration(
                actor, true, createCommand(dto)), authorization.currentSnapshot(actor).permissions()), traceId);
    }

    @PostMapping("/configurations/{modelConfigId}/impact")
    public ApiResponse<Map<String, Object>> previewUserUpdate(
            Authentication authentication,
            @PathVariable String modelConfigId,
            @Valid @RequestBody UpdateModelConfigurationDto dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(impactView(service.previewUpdate(actor(authentication),
                id(modelConfigId, "modelConfigId"), false, updateCommand(dto))), traceId);
    }

    @PostMapping("/system-configurations/{modelConfigId}/impact")
    @PreAuthorize("hasAuthority('model:system:write')")
    public ApiResponse<Map<String, Object>> previewSystemUpdate(
            Authentication authentication,
            @PathVariable String modelConfigId,
            @Valid @RequestBody UpdateModelConfigurationDto dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(impactView(service.previewUpdate(actor(authentication),
                id(modelConfigId, "modelConfigId"), true, updateCommand(dto))), traceId);
    }

    @PutMapping("/configurations/{modelConfigId}")
    public ApiResponse<Map<String, Object>> updateUserConfiguration(
            Authentication authentication,
            @PathVariable String modelConfigId,
            @Valid @RequestBody UpdateModelConfigurationDto dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Long actor = actor(authentication);
        return ApiResponse.success(configurationView(service.updateConfiguration(actor,
                id(modelConfigId, "modelConfigId"), false, updateCommand(dto)),
                authorization.currentSnapshot(actor).permissions()), traceId);
    }

    @PutMapping("/system-configurations/{modelConfigId}")
    @PreAuthorize("hasAuthority('model:system:write')")
    public ApiResponse<Map<String, Object>> updateSystemConfiguration(
            Authentication authentication,
            @PathVariable String modelConfigId,
            @Valid @RequestBody UpdateModelConfigurationDto dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        Long actor = actor(authentication);
        return ApiResponse.success(configurationView(service.updateConfiguration(actor,
                id(modelConfigId, "modelConfigId"), true, updateCommand(dto)),
                authorization.currentSnapshot(actor).permissions()), traceId);
    }

    @PostMapping("/configurations/{modelConfigId}/unbind")
    public ApiResponse<Map<String, Object>> unbindUserConfiguration(
            Authentication authentication,
            @PathVariable String modelConfigId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        int count = service.unbindAll(actor(authentication), id(modelConfigId, "modelConfigId"), false);
        return ApiResponse.success(Map.of("unboundProjectCount", count), traceId);
    }

    @PostMapping("/system-configurations/{modelConfigId}/unbind")
    @PreAuthorize("hasAuthority('model:system:write')")
    public ApiResponse<Map<String, Object>> unbindSystemConfiguration(
            Authentication authentication,
            @PathVariable String modelConfigId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        int count = service.unbindAll(actor(authentication), id(modelConfigId, "modelConfigId"), true);
        return ApiResponse.success(Map.of("unboundProjectCount", count), traceId);
    }

    @DeleteMapping("/configurations/{modelConfigId}")
    public ApiResponse<String> deleteUserConfiguration(Authentication authentication,
                                                       @PathVariable String modelConfigId,
                                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        service.deleteConfiguration(actor(authentication), id(modelConfigId, "modelConfigId"), false);
        return ApiResponse.success("deleted", traceId);
    }

    @DeleteMapping("/system-configurations/{modelConfigId}")
    @PreAuthorize("hasAuthority('model:system:write')")
    public ApiResponse<String> deleteSystemConfiguration(Authentication authentication,
                                                         @PathVariable String modelConfigId,
                                                         @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        service.deleteConfiguration(actor(authentication), id(modelConfigId, "modelConfigId"), true);
        return ApiResponse.success("deleted", traceId);
    }

    @GetMapping("/preferences")
    public ApiResponse<Map<String, Object>> getPreferences(
            Authentication authentication,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(preferencesView(service.getUserPreferences(actor(authentication))), traceId);
    }

    @PutMapping("/preferences")
    public ApiResponse<Map<String, Object>> savePreferences(
            Authentication authentication,
            @Valid @RequestBody SaveModelPreferencesDto dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        ModelCommands.SaveUserModelPreferencesCommand command = new ModelCommands.SaveUserModelPreferencesCommand(
                optionalId(dto.getDefaultCreativeModelConfigId(), "defaultCreativeModelConfigId"),
                optionalId(dto.getDefaultContextSelectorModelConfigId(), "defaultContextSelectorModelConfigId"),
                optionalId(dto.getDefaultEmbeddingModelConfigId(), "defaultEmbeddingModelConfigId"),
                dto.getDefaultStoryBibleRoutingMode(), dto.getDefaultChunkTargetCharacters(),
                dto.getDefaultChunkOverlapCharacters(), dto.getDefaultChunkMaxCharacters());
        return ApiResponse.success(preferencesView(service.saveUserPreferences(actor(authentication), command)), traceId);
    }

    private ModelCommands.CreateConfigurationCommand createCommand(CreateModelConfigurationDto dto) {
        return new ModelCommands.CreateConfigurationCommand(id(dto.getProviderId(), "providerId"), dto.getDisplayName(),
                dto.getModelType(), dto.getModelName(), dto.getBaseUrl(), dto.getDistanceMetric(),
                dto.getEmbeddingDimensions(), dto.getApiKey(),
                dto.getContextWindowTurns(), dto.getMaxContextTokens(), dto.getMaxOutputTokens(),
                dto.getAutoDetectCapacity());
    }

    private ModelCommands.UpdateConfigurationCommand updateCommand(UpdateModelConfigurationDto dto) {
        return new ModelCommands.UpdateConfigurationCommand(optionalId(dto.getProviderId(), "providerId"),
                dto.getDisplayName(), dto.getModelName(), dto.getBaseUrl(), dto.getDistanceMetric(),
                dto.getEmbeddingDimensions(), dto.isEmbeddingDimensionsSet(), dto.getApiKey(), dto.getContextWindowTurns(),
                dto.getMaxContextTokens(), dto.getMaxOutputTokens(), dto.getAutoDetectCapacity(), dto.getStatus());
    }

    private ModelCommands.ProbeEmbeddingDimensionCommand probeCommand(ProbeEmbeddingDimensionDto dto) {
        return new ModelCommands.ProbeEmbeddingDimensionCommand(
                optionalId(dto.getModelConfigId(), "modelConfigId"),
                optionalId(dto.getProviderId(), "providerId"), dto.getModelName(), dto.getBaseUrl(),
                dto.getEmbeddingDimensions(), dto.getApiKey());
    }

    private ModelCommands.DiscoverModelsCommand discoveryCommand(DiscoverModelsDto dto) {
        return new ModelCommands.DiscoverModelsCommand(
                optionalId(dto.getModelConfigId(), "modelConfigId"),
                optionalId(dto.getProviderId(), "providerId"), dto.getModelType(),
                dto.getBaseUrl(), dto.getApiKey());
    }

    private Map<String, Object> probeView(ModelApplicationService.EmbeddingDimensionProbeResult result) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("dimensions", result.dimensions());
        view.put("requestedDimensions", result.requestedDimensions());
        view.put("nativeMode", result.nativeMode());
        return view;
    }

    private Map<String, Object> providerView(ModelApplicationService.ProviderView view) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("providerId", string(view.provider().getProviderId()));
        result.put("code", view.provider().getCode());
        result.put("name", view.provider().getName());
        result.put("baseUrl", view.provider().getBaseUrl());
        result.put("authType", view.provider().getAuthType());
        result.put("capabilities", view.capabilities().stream().map(this::capabilityView).toList());
        return result;
    }

    private Map<String, Object> capabilityView(ModelProviderCapability capability) {
        return Map.of("capabilityCode", capability.getCapabilityCode(), "protocolCode", capability.getProtocolCode());
    }

    private Map<String, Object> configurationView(ModelConfiguration configuration, Set<String> permissions) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelConfigId", string(configuration.getModelConfigId()));
        result.put("scopeType", configuration.getScopeType());
        result.put("ownerUserId", string(configuration.getOwnerUserId()));
        result.put("providerId", string(configuration.getProviderId()));
        result.put("providerCode", configuration.getProviderCode());
        result.put("providerName", configuration.getProviderName());
        result.put("protocolCode", configuration.getProtocolCode());
        result.put("displayName", configuration.getDisplayName());
        result.put("modelType", configuration.getModelType());
        result.put("modelName", configuration.getModelName());
        result.put("baseUrl", configuration.getBaseUrl());
        result.put("distanceMetric", configuration.getDistanceMetric());
        result.put("embeddingDimensions", configuration.getEmbeddingDimensions());
        result.put("contextWindowTurns", configuration.getContextWindowTurns());
        result.put("maxContextTokens", configuration.getMaxContextTokens());
        result.put("maxOutputTokens", configuration.getMaxOutputTokens());
        result.put("contextCapacitySource", configuration.getContextCapacitySource());
        result.put("contextCapacitySourceUrl", configuration.getContextCapacitySourceUrl());
        result.put("contextCapacityVerifiedAt", configuration.getContextCapacityVerifiedAt());
        result.put("contextCapacitySource", configuration.getContextCapacitySource());
        result.put("contextCapacitySourceUrl", configuration.getContextCapacitySourceUrl());
        result.put("contextCapacityVerifiedAt", configuration.getContextCapacityVerifiedAt());
        result.put("maskedApiKey", configuration.getMaskedApiKey());
        result.put("credentialConfigured", configuration.getMaskedApiKey() != null
                || "NONE".equalsIgnoreCase(configuration.getProviderAuthType()));
        result.put("status", configuration.getStatus());
        result.put("lastTestStatus", configuration.getLastTestStatus());
        result.put("lastTestLatencyMs", configuration.getLastTestLatencyMs());
        result.put("lastTestError", configuration.getLastTestError());
        result.put("lastTestedAt", configuration.getLastTestedAt());
        result.put("createdAt", configuration.getCreatedAt());
        result.put("updatedAt", configuration.getUpdatedAt());
        boolean official = "SYSTEM".equalsIgnoreCase(configuration.getScopeType());
        boolean usable = permissions.contains(official
                ? IamPermissionCodes.MODEL_OFFICIAL_USE
                : IamPermissionCodes.MODEL_USER_USE);
        result.put("usable", usable);
        result.put("unavailableReason", usable ? null
                : official ? "OFFICIAL_MODEL_PERMISSION_REQUIRED" : "USER_MODEL_PERMISSION_REQUIRED");
        return result;
    }

    private Map<String, Object> preferencesView(ModelUserPreferences preferences) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", string(preferences.getUserId()));
        result.put("defaultCreativeModelConfigId", string(preferences.getDefaultCreativeModelConfigId()));
        result.put("defaultContextSelectorModelConfigId", string(preferences.getDefaultContextSelectorModelConfigId()));
        result.put("defaultEmbeddingModelConfigId", string(preferences.getDefaultEmbeddingModelConfigId()));
        result.put("defaultStoryBibleRoutingMode", preferences.getDefaultStoryBibleRoutingMode());
        result.put("defaultChunkTargetCharacters", preferences.getDefaultChunkTargetCharacters());
        result.put("defaultChunkOverlapCharacters", preferences.getDefaultChunkOverlapCharacters());
        result.put("defaultChunkMaxCharacters", preferences.getDefaultChunkMaxCharacters());
        return result;
    }

    private Map<String, Object> impactView(ModelApplicationService.ImpactPreview impact) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("modelConfigId", string(impact.modelConfigId()));
        result.put("modelType", impact.modelType());
        result.put("embeddingIdentityChange", impact.embeddingIdentityChange());
        result.put("affectedProjectCount", impact.projectIds().size());
        result.put("affectedProjectIds", impact.projectIds().stream().map(String::valueOf).toList());
        result.put("blockedByRun", impact.blockedByRun());
        return result;
    }

    private Long actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) throw BusinessException.of("Login required");
        return id(authentication.getName(), "principal userId");
    }

    private Long optionalId(String value, String field) {
        return value == null || value.isBlank() ? null : id(value, field);
    }

    private Long id(String value, String field) {
        if (value == null || !value.trim().matches("^[1-9]\\d*$")) throw BusinessException.of(field + " is invalid");
        try { return Long.parseLong(value.trim()); }
        catch (NumberFormatException exception) { throw BusinessException.of(field + " is invalid"); }
    }

    private String string(Long value) { return value == null ? null : value.toString(); }
}
