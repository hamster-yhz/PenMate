package com.penmate.backend.interfaces.api.model;

import com.penmate.backend.application.model.ModelApplicationService;
import com.penmate.backend.application.model.command.ModelCommands;
import com.penmate.backend.domain.model.model.ModelProjectPolicy;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelProviderModel;
import com.penmate.backend.domain.model.model.ModelUserApiKey;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import com.penmate.backend.interfaces.api.model.dto.CreateModelKeyDto;
import com.penmate.backend.interfaces.api.model.dto.CreateModelPolicyDto;
import com.penmate.backend.interfaces.api.model.dto.UpdateModelKeyDto;
import com.penmate.backend.interfaces.api.model.dto.UpdateModelPolicyDto;
import jakarta.validation.Valid;
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

import java.util.List;

/**
 * ModelController。
 * <p>控制层：负责HTTP请求接入、参数校验与统一响应封装。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class ModelController {

    private final ModelApplicationService modelApplicationService;

    public ModelController(ModelApplicationService modelApplicationService) {
        this.modelApplicationService = modelApplicationService;
    }

    /**
     * 查询列表数据。
     *
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/model/providers")
    public ApiResponse<List<ModelProvider>> listProviders(@RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(modelApplicationService.listProviders(), traceId);
    }

    /**
     * 查询列表数据。
     *
     * @param providerCode 入参：providerCode
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/model/providers/{providerCode}/models")
    public ApiResponse<List<ModelProviderModel>> listProviderModels(@PathVariable String providerCode,
                                                                    @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(modelApplicationService.listProviderModels(providerCode), traceId);
    }

    /**
     * 查询列表数据。
     *
     * @param userId 入参：userId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/model/keys")
    public ApiResponse<List<ModelUserApiKey>> listKeys(@RequestParam("userId") Long userId,
                                                       @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(modelApplicationService.listUserKeys(userId), traceId);
    }

    /**
     * 创建业务数据。
     *
     * @param dto 入参：dto
     * @param userId 入参：userId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/model/keys")
    public ApiResponse<String> createKey(@Valid @RequestBody CreateModelKeyDto dto,
                                         @RequestParam("userId") Long userId,
                                         @RequestParam("operatorId") Long operatorId,
                                         @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.createKey(
                userId,
                new ModelCommands.CreateModelKeyCommand(
                        dto.getProviderId(),
                        dto.getKeyName(),
                        dto.getApiKey(),
                        dto.getIsDefault(),
                        dto.getStatus(),
                        operatorId
                ),
                traceId
        );
        return ApiResponse.success("created", traceId);
    }

    /**
     * 更新业务数据。
     *
     * @param keyId 入参：keyId
     * @param dto 入参：dto
     * @param userId 入参：userId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PatchMapping("/model/keys/{keyId}")
    public ApiResponse<String> updateKey(@PathVariable Long keyId,
                                         @RequestBody UpdateModelKeyDto dto,
                                         @RequestParam("userId") Long userId,
                                         @RequestParam("operatorId") Long operatorId,
                                         @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.updateKey(
                userId,
                keyId,
                new ModelCommands.UpdateModelKeyCommand(
                        dto.getKeyName(),
                        dto.getApiKey(),
                        dto.getIsDefault(),
                        dto.getStatus(),
                        operatorId
                ),
                traceId
        );
        return ApiResponse.success("updated", traceId);
    }

    /**
     * 删除业务数据。
     *
     * @param keyId 入参：keyId
     * @param userId 入参：userId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @DeleteMapping("/model/keys/{keyId}")
    public ApiResponse<String> deleteKey(@PathVariable Long keyId,
                                         @RequestParam("userId") Long userId,
                                         @RequestParam("operatorId") Long operatorId,
                                         @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.deleteKey(userId, keyId, operatorId, traceId);
        return ApiResponse.success("deleted", traceId);
    }

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @GetMapping("/novels/{projectId}/model-policies")
    public ApiResponse<List<ModelProjectPolicy>> listPolicies(@PathVariable Long projectId,
                                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(modelApplicationService.listPolicies(projectId), traceId);
    }

    /**
     * 创建业务数据。
     *
     * @param projectId 入参：projectId
     * @param dto 入参：dto
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/novels/{projectId}/model-policies")
    public ApiResponse<String> createPolicy(@PathVariable Long projectId,
                                            @Valid @RequestBody CreateModelPolicyDto dto,
                                            @RequestParam("operatorId") Long operatorId,
                                            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.createPolicy(
                projectId,
                new ModelCommands.CreatePolicyCommand(
                        dto.getPolicyName(),
                        dto.getScene(),
                        dto.getProviderModelId(),
                        dto.getUserKeyId(),
                        dto.getTemperature(),
                        dto.getTopP(),
                        dto.getMaxTokens(),
                        dto.getFallbackPolicyJson(),
                        dto.getIsDefault(),
                        operatorId
                ),
                traceId
        );
        return ApiResponse.success("created", traceId);
    }

    /**
     * 更新业务数据。
     *
     * @param projectId 入参：projectId
     * @param policyId 入参：policyId
     * @param dto 入参：dto
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PutMapping("/novels/{projectId}/model-policies/{policyId}")
    public ApiResponse<String> updatePolicy(@PathVariable Long projectId,
                                            @PathVariable Long policyId,
                                            @RequestBody UpdateModelPolicyDto dto,
                                            @RequestParam("operatorId") Long operatorId,
                                            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.updatePolicy(
                projectId,
                policyId,
                new ModelCommands.UpdatePolicyCommand(
                        dto.getPolicyName(),
                        dto.getScene(),
                        dto.getProviderModelId(),
                        dto.getUserKeyId(),
                        dto.getTemperature(),
                        dto.getTopP(),
                        dto.getMaxTokens(),
                        dto.getFallbackPolicyJson(),
                        dto.getIsDefault(),
                        operatorId
                ),
                traceId
        );
        return ApiResponse.success("updated", traceId);
    }

    /**
     * 删除业务数据。
     *
     * @param projectId 入参：projectId
     * @param policyId 入参：policyId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @DeleteMapping("/novels/{projectId}/model-policies/{policyId}")
    public ApiResponse<String> deletePolicy(@PathVariable Long projectId,
                                            @PathVariable Long policyId,
                                            @RequestParam("operatorId") Long operatorId,
                                            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.deletePolicy(projectId, policyId, operatorId, traceId);
        return ApiResponse.success("deleted", traceId);
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param policyId 入参：policyId
     * @param operatorId 入参：operatorId
     * @param traceId 入参：traceId
     * @return 出参：处理结果
     */
    @PostMapping("/novels/{projectId}/model-policies/{policyId}/set-default")
    public ApiResponse<String> setDefaultPolicy(@PathVariable Long projectId,
                                                @PathVariable Long policyId,
                                                @RequestParam("operatorId") Long operatorId,
                                                @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.setDefaultPolicy(projectId, policyId, operatorId, traceId);
        return ApiResponse.success("updated", traceId);
    }
}

