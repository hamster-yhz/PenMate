package com.penmate.backend.interfaces.api.model;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.application.model.ModelApplicationService;
import com.penmate.backend.application.model.command.ModelCommands;
import com.penmate.backend.domain.model.model.ModelOfficialApiKey;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelUserApiKey;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import com.penmate.backend.interfaces.api.model.dto.CreateModelKeyDto;
import com.penmate.backend.interfaces.api.model.dto.CreateOfficialModelKeyDto;
import com.penmate.backend.interfaces.api.model.dto.CreateUserModelConfigDto;
import com.penmate.backend.interfaces.api.model.dto.SaveUserModelPreferencesDto;
import com.penmate.backend.interfaces.api.model.dto.UpdateModelKeyDto;
import com.penmate.backend.interfaces.api.model.dto.UpdateOfficialModelKeyDto;
import com.penmate.backend.interfaces.api.model.dto.UpdateUserModelConfigDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.temporal.TemporalAccessor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ModelController {

    private static final Set<String> BUSINESS_ID_KEYS = Set.of(
            "id",
            "userId",
            "providerId",
            "modelConfigId",
            "mainAgentModelConfigId",
            "dirtyWorkAgentModelConfigId"
    );

    private final ModelApplicationService modelApplicationService;

    @GetMapping("/model/providers")
    public ApiResponse<List<Map<String, Object>>> listProviders(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        List<Map<String, Object>> items = modelApplicationService.listProviders().stream()
                .filter(provider -> provider.getProviderId() != null && provider.getProviderId() > 0)
                .map(this::toProviderView)
                .toList();
        return ApiResponse.success(items, traceId);
    }

    @GetMapping("/model/keys")
    public ApiResponse<List<Map<String, Object>>> listKeys(
            @RequestParam("userId") String userId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        List<Map<String, Object>> items = modelApplicationService.listUserKeys(parseRequiredPositiveId(userId, "userId")).stream()
                .map(this::toUserKeyView)
                .toList();
        return ApiResponse.success(items, traceId);
    }

    @PostMapping("/model/keys")
    public ApiResponse<String> createKey(
            @RequestParam("userId") String userId,
            @RequestParam("operatorId") String operatorId,
            @Valid @RequestBody CreateModelKeyDto dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.createKey(
                parseRequiredPositiveId(userId, "userId"),
                new ModelCommands.CreateModelKeyCommand(
                        parseRequiredPositiveId(dto.getProviderId(), "providerId"),
                        dto.getKeyName(),
                        dto.getApiKey(),
                        dto.getIsDefault(),
                        dto.getStatus(),
                        parseRequiredPositiveId(operatorId, "operatorId")
                ),
                traceId
        );
        return ApiResponse.success("created", traceId);
    }

    @PatchMapping("/model/keys/{keyId}")
    public ApiResponse<String> updateKey(
            @PathVariable String keyId,
            @RequestParam("userId") String userId,
            @RequestParam("operatorId") String operatorId,
            @RequestBody UpdateModelKeyDto dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.updateKey(
                parseRequiredPositiveId(userId, "userId"),
                parseRequiredPositiveId(keyId, "keyId"),
                new ModelCommands.UpdateModelKeyCommand(
                        dto.getKeyName(),
                        dto.getApiKey(),
                        dto.getIsDefault(),
                        dto.getStatus(),
                        parseRequiredPositiveId(operatorId, "operatorId")
                ),
                traceId
        );
        return ApiResponse.success("updated", traceId);
    }

    @DeleteMapping("/model/keys/{keyId}")
    public ApiResponse<String> deleteKey(
            @PathVariable String keyId,
            @RequestParam("userId") String userId,
            @RequestParam("operatorId") String operatorId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.deleteKey(
                parseRequiredPositiveId(userId, "userId"),
                parseRequiredPositiveId(keyId, "keyId"),
                parseRequiredPositiveId(operatorId, "operatorId"),
                traceId
        );
        return ApiResponse.success("deleted", traceId);
    }

    @GetMapping("/model/official-keys")
    public ApiResponse<List<Map<String, Object>>> listOfficialKeys(
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        List<Map<String, Object>> items = modelApplicationService.listOfficialKeys().stream()
                .map(this::toOfficialKeyView)
                .toList();
        return ApiResponse.success(items, traceId);
    }

    @PostMapping("/model/official-keys")
    public ApiResponse<String> createOfficialKey(
            @RequestParam("operatorId") String operatorId,
            @Valid @RequestBody CreateOfficialModelKeyDto dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.createOfficialKey(
                new ModelCommands.CreateOfficialModelKeyCommand(
                        parseRequiredPositiveId(dto.getProviderId(), "providerId"),
                        dto.getKeyName(),
                        dto.getApiKey(),
                        dto.getIsDefault(),
                        dto.getStatus(),
                        parseRequiredPositiveId(operatorId, "operatorId")
                ),
                traceId
        );
        return ApiResponse.success("created", traceId);
    }

    @PatchMapping("/model/official-keys/{keyId}")
    public ApiResponse<String> updateOfficialKey(
            @PathVariable String keyId,
            @RequestParam("operatorId") String operatorId,
            @RequestBody UpdateOfficialModelKeyDto dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.updateOfficialKey(
                parseRequiredPositiveId(keyId, "keyId"),
                new ModelCommands.UpdateOfficialModelKeyCommand(
                        dto.getKeyName(),
                        dto.getApiKey(),
                        dto.getIsDefault(),
                        dto.getStatus(),
                        parseRequiredPositiveId(operatorId, "operatorId")
                ),
                traceId
        );
        return ApiResponse.success("updated", traceId);
    }

    @DeleteMapping("/model/official-keys/{keyId}")
    public ApiResponse<String> deleteOfficialKey(
            @PathVariable String keyId,
            @RequestParam("operatorId") String operatorId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.deleteOfficialKey(
                parseRequiredPositiveId(keyId, "keyId"),
                parseRequiredPositiveId(operatorId, "operatorId"),
                traceId
        );
        return ApiResponse.success("deleted", traceId);
    }

    @GetMapping("/model/configs")
    public ApiResponse<List<Map<String, Object>>> listUserModelConfigs(
            @RequestParam("userId") String userId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        List<Map<String, Object>> items = modelApplicationService.listUserModelConfigs(parseRequiredPositiveId(userId, "userId")).stream()
                .map(this::stringifyBusinessIds)
                .toList();
        return ApiResponse.success(items, traceId);
    }

    @PostMapping("/model/configs")
    public ApiResponse<String> createUserModelConfig(
            @RequestParam("userId") String userId,
            @RequestParam("operatorId") String operatorId,
            @Valid @RequestBody CreateUserModelConfigDto dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.createUserModelConfig(
                parseRequiredPositiveId(userId, "userId"),
                new ModelCommands.CreateUserModelConfigCommand(
                        parseRequiredPositiveId(dto.getProviderId(), "providerId"),
                        dto.getModelName(),
                        dto.getBaseUrl(),
                        mapModelCategoryToKeySourceType(dto.getModelCategory()),
                        dto.getApiKey(),
                        dto.getStatus(),
                        parseRequiredPositiveId(operatorId, "operatorId")
                ),
                traceId
        );
        return ApiResponse.success("created", traceId);
    }

    @PutMapping("/model/configs/{modelConfigId}")
    public ApiResponse<String> updateUserModelConfig(
            @PathVariable String modelConfigId,
            @RequestParam("userId") String userId,
            @RequestParam("operatorId") String operatorId,
            @Valid @RequestBody UpdateUserModelConfigDto dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.updateUserModelConfig(
                parseRequiredPositiveId(userId, "userId"),
                parseRequiredPositiveId(modelConfigId, "modelConfigId"),
                new ModelCommands.UpdateUserModelConfigCommand(
                        parseOptionalPositiveId(dto.getProviderId(), "providerId"),
                        dto.getModelName(),
                        dto.getBaseUrl(),
                        mapModelCategoryToKeySourceType(dto.getModelCategory()),
                        dto.getApiKey(),
                        dto.getStatus(),
                        parseRequiredPositiveId(operatorId, "operatorId")
                ),
                traceId
        );
        return ApiResponse.success("updated", traceId);
    }

    @DeleteMapping("/model/configs/{modelConfigId}")
    public ApiResponse<String> deleteUserModelConfig(
            @PathVariable String modelConfigId,
            @RequestParam("userId") String userId,
            @RequestParam("operatorId") String operatorId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.deleteUserModelConfig(
                parseRequiredPositiveId(userId, "userId"),
                parseRequiredPositiveId(modelConfigId, "modelConfigId"),
                parseRequiredPositiveId(operatorId, "operatorId"),
                traceId
        );
        return ApiResponse.success("deleted", traceId);
    }

    private Map<String, Object> toProviderView(ModelProvider provider) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("providerId", stringifyId(provider.getProviderId()));
        data.put("code", provider.getCode());
        data.put("name", provider.getName());
        data.put("baseUrl", provider.getBaseUrl());
        data.put("authType", provider.getAuthType());
        data.put("status", provider.getStatus());
        return data;
    }

    private Map<String, Object> toUserKeyView(ModelUserApiKey key) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", stringifyId(key.getUserApiKeyId()));
        data.put("userId", stringifyId(key.getUserId()));
        data.put("providerId", stringifyId(key.getProviderId()));
        data.put("keyName", key.getKeyName());
        data.put("maskedApiKey", key.getMaskedApiKey());
        data.put("isDefault", key.getIsDefault());
        data.put("lastUsedAt", key.getLastUsedAt());
        data.put("status", key.getStatus());
        data.put("createdAt", key.getCreatedAt());
        data.put("updatedAt", key.getUpdatedAt());
        return data;
    }

    private Map<String, Object> toOfficialKeyView(ModelOfficialApiKey key) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", stringifyId(key.getOfficialApiKeyId()));
        data.put("providerId", stringifyId(key.getProviderId()));
        data.put("keyName", key.getKeyName());
        data.put("maskedApiKey", key.getMaskedApiKey());
        data.put("isDefault", key.getIsDefault());
        data.put("lastUsedAt", key.getLastUsedAt());
        data.put("status", key.getStatus());
        data.put("createdAt", key.getCreatedAt());
        data.put("updatedAt", key.getUpdatedAt());
        return data;
    }

    @GetMapping("/model/preferences")
    public ApiResponse<Map<String, Object>> getUserModelPreferences(
            @RequestParam("userId") String userId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(
                stringifyBusinessIds(modelApplicationService.getUserModelPreferencesDetail(parseRequiredPositiveId(userId, "userId"))),
                traceId
        );
    }

    @PostMapping("/model/preferences")
    public ApiResponse<String> saveUserModelPreferences(
            @RequestParam("userId") String userId,
            @RequestParam("operatorId") String operatorId,
            @Valid @RequestBody SaveUserModelPreferencesDto dto,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.saveUserModelPreferences(
                parseRequiredPositiveId(userId, "userId"),
                parseRequiredPositiveId(operatorId, "operatorId"),
                new ModelCommands.SaveUserModelPreferencesCommand(
                        parseOptionalPositiveId(dto.getMainAgentModelConfigId(), "mainAgentModelConfigId"),
                        parseOptionalPositiveId(dto.getDirtyWorkAgentModelConfigId(), "dirtyWorkAgentModelConfigId")
                ),
                traceId
        );
        return ApiResponse.success("updated", traceId);
    }

    private String mapModelCategoryToKeySourceType(String modelCategory) {
        if (modelCategory == null) {
            return null;
        }
        return switch (modelCategory.trim().toUpperCase()) {
            case "OFFICIAL_MODEL" -> "OFFICIAL_KEY";
            case "USER_MODEL" -> "USER_KEY";
            default -> modelCategory.trim();
        };
    }

    private Long parseRequiredPositiveId(String rawValue, String fieldName) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            throw BusinessException.of(fieldName + " is required");
        }
        return parsePositiveId(rawValue, fieldName);
    }

    private Long parseOptionalPositiveId(String rawValue, String fieldName) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }
        return parsePositiveId(rawValue, fieldName);
    }

    private Long parsePositiveId(String rawValue, String fieldName) {
        String normalized = rawValue.trim();
        if (!normalized.matches("^[1-9]\\d*$")) {
            throw BusinessException.of(fieldName + " must be greater than 0");
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException ex) {
            throw BusinessException.of(fieldName + " must be greater than 0");
        }
    }

    private String stringifyId(Long value) {
        return value == null ? null : value.toString();
    }

    private Map<String, Object> stringifyBusinessIds(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            result.put(entry.getKey(), stringifyValue(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private Object stringifyValue(String key, Object value) {
        if (value == null) {
            return null;
        }
        if (BUSINESS_ID_KEYS.contains(key) && value instanceof Number number) {
            return String.valueOf(number.longValue());
        }
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (Map.Entry<?, ?> nestedEntry : mapValue.entrySet()) {
                nested.put(String.valueOf(nestedEntry.getKey()), stringifyValue(String.valueOf(nestedEntry.getKey()), nestedEntry.getValue()));
            }
            return nested;
        }
        if (value instanceof List<?> listValue) {
            return listValue.stream().map(item -> {
                if (item instanceof Map<?, ?> mapItem) {
                    Map<String, Object> nested = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> nestedEntry : mapItem.entrySet()) {
                        nested.put(String.valueOf(nestedEntry.getKey()), stringifyValue(String.valueOf(nestedEntry.getKey()), nestedEntry.getValue()));
                    }
                    return nested;
                }
                return item;
            }).toList();
        }
        if (value instanceof TemporalAccessor) {
            return value;
        }
        return value;
    }
}
