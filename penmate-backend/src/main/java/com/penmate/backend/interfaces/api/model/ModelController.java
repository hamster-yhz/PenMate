package com.penmate.backend.interfaces.api.model;

import com.penmate.backend.application.model.ModelApplicationService;
import com.penmate.backend.application.model.command.ModelCommands;
import com.penmate.backend.domain.model.model.ModelProjectPolicy;
import com.penmate.backend.domain.model.model.ModelOfficialApiKey;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelUserApiKey;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import com.penmate.backend.interfaces.api.model.dto.CreateModelKeyDto;
import com.penmate.backend.interfaces.api.model.dto.CreateModelPolicyDto;
import com.penmate.backend.interfaces.api.model.dto.SaveUserModelPreferencesDto;
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
import java.util.Map;

/**
 * 模型供应商、密钥与项目模型配置控制器。
 * <p>负责内置供应商目录查询、用户 API Key 管理、项目模型配置管理与默认配置切换。</p>
 * <p>说明：底层存储仍复用历史 policy 表结构，接口语义已切换为 config。</p>
 */
@RestController
@RequestMapping("/api/v1")
public class ModelController {

    private final ModelApplicationService modelApplicationService;

    public ModelController(ModelApplicationService modelApplicationService) {
        this.modelApplicationService = modelApplicationService;
    }

    /**
     * 查询模型供应商列表。
     * <p><b>业务目的：</b>返回系统支持的模型厂商，用于前端创建密钥与策略时选择供应商。</p>
     * <p><b>流程主线：</b>接收请求 -> 调用应用服务查询供应商 -> 封装统一响应。</p>
     * <p><b>关键调用：</b>{@code modelApplicationService.listProviders()}。</p>
     * <p><b>异常与分支：</b>无供应商数据时返回空列表。</p>
     * <p><b>副作用：</b>无持久化写入。</p>
     */
    @GetMapping("/model/providers")
    public ApiResponse<List<ModelProvider>> listProviders(@RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(modelApplicationService.listProviders(), traceId);
    }

    /**
     * 查询用户模型密钥列表。
     * <p><b>业务目的：</b>返回用户已维护的 API Key 元数据，供前端密钥管理页展示。</p>
     * <p><b>流程主线：</b>读取用户业务ID -> 调用应用服务查询密钥列表 -> 封装响应。</p>
     * <p><b>关键调用：</b>{@code modelApplicationService.listUserKeys(userId)}。</p>
     * <p><b>异常与分支：</b>用户不存在时返回业务异常。</p>
     * <p><b>副作用：</b>无持久化写入。</p>
     */
    @GetMapping("/model/keys")
    public ApiResponse<List<ModelUserApiKey>> listKeys(@RequestParam("userId") Long userId,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(modelApplicationService.listUserKeys(userId), traceId);
    }

    @GetMapping("/model/official-keys")
    public ApiResponse<List<ModelOfficialApiKey>> listOfficialKeys(@RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(modelApplicationService.listOfficialKeys(), traceId);
    }

    /**
     * 查询用户模型偏好详情。
     * <p><b>业务目的：</b>返回用户当前主 Agent / 脏活 Agent 选择及候选模型配置，供个人中心展示与编辑。</p>
     * <p><b>流程主线：</b>读取用户业务 ID -> 查询用户偏好详情 -> 封装统一响应。</p>
     * <p><b>关键调用：</b>{@code modelApplicationService.getUserModelPreferencesDetail(userId)}。</p>
     * <p><b>异常与分支：</b>用户不存在时返回业务异常。</p>
     * <p><b>副作用：</b>无持久化写入。</p>
     */
    @GetMapping("/model/preferences")
    public ApiResponse<Map<String, Object>> getUserModelPreferencesDetail(@RequestParam("userId") Long userId,
                                                                          @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(modelApplicationService.getUserModelPreferencesDetail(userId), traceId);
    }

    /**
     * 保存用户模型偏好。
     * <p><b>业务目的：</b>维护用户在主 Agent 与脏活 Agent 两类执行场景下的默认模型配置。</p>
     * <p><b>流程主线：</b>校验配置 ID -> 组装命令对象 -> 调用应用服务保存偏好 -> 返回确认结果。</p>
     * <p><b>关键调用：</b>{@code modelApplicationService.saveUserModelPreferences(...)}。</p>
     * <p><b>异常与分支：</b>配置 ID 非法、配置不可用或用户不存在时返回校验/业务异常。</p>
     * <p><b>副作用：</b>更新 iam_users 上的模型偏好字段。</p>
     */
    @PostMapping("/model/preferences")
    public ApiResponse<String> saveUserModelPreferences(@RequestParam("userId") Long userId,
                                                        @RequestParam("operatorId") Long operatorId,
                                                        @Valid @RequestBody SaveUserModelPreferencesDto dto,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.saveUserModelPreferences(
                userId,
                operatorId,
                new ModelCommands.SaveUserModelPreferencesCommand(
                        dto.getMainAgentModelConfigId(),
                        dto.getDirtyWorkAgentModelConfigId()
                ),
                traceId
        );
        return ApiResponse.success("updated", traceId);
    }

    /**
     * 创建用户模型密钥。
     * <p><b>业务目的：</b>为用户新增一条可用于模型调用的 API Key 配置。</p>
     * <p><b>流程主线：</b>校验请求体 -> 组装 {@link ModelCommands.CreateModelKeyCommand} -> 调用应用服务创建 -> 返回确认结果。</p>
     * <p><b>关键调用：</b>{@code modelApplicationService.createKey(...)} 负责密钥加密存储与默认项处理。</p>
     * <p><b>异常与分支：</b>供应商无效、密钥重复或权限不足时返回业务异常。</p>
     * <p><b>副作用：</b>新增密钥记录，可能变更默认密钥。</p>
     * <p><b>ID 语义：</b>所有 userId/providerId/keyId 均为业务语义 ID。</p>
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
     * 更新用户模型密钥。
     * <p><b>业务目的：</b>修改密钥名称、值、启用状态或默认标记。</p>
     * <p><b>流程主线：</b>读取密钥标识 -> 组装 {@link ModelCommands.UpdateModelKeyCommand} -> 调用应用服务更新 -> 返回确认结果。</p>
     * <p><b>关键调用：</b>{@code modelApplicationService.updateKey(...)}。</p>
     * <p><b>异常与分支：</b>密钥不存在、权限不足或状态非法时返回业务异常。</p>
     * <p><b>副作用：</b>更新密钥元数据与加密值。</p>
     * <p><b>ID 语义：</b>所有 userId/keyId 均为业务语义 ID。</p>
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
     * 删除用户模型密钥。
     * <p><b>业务目的：</b>移除不再使用或存在风险的 API Key。</p>
     * <p><b>流程主线：</b>接收 keyId 与操作者 -> 调用应用服务删除密钥 -> 返回确认结果。</p>
     * <p><b>关键调用：</b>{@code modelApplicationService.deleteKey(userId, keyId, operatorId, traceId)}。</p>
     * <p><b>异常与分支：</b>密钥不存在、被策略引用或权限不足时返回业务异常。</p>
     * <p><b>副作用：</b>删除密钥记录，可能影响策略可用性。</p>
     * <p><b>ID 语义：</b>所有 userId/keyId 均为业务语义 ID。</p>
     */
    @DeleteMapping("/model/keys/{keyId}")
    public ApiResponse<String> deleteKey(@PathVariable Long keyId,
                                         @RequestParam("userId") Long userId,
                                         @RequestParam("operatorId") Long operatorId,
                                         @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.deleteKey(userId, keyId, operatorId, traceId);
        return ApiResponse.success("deleted", traceId);
    }

    @PostMapping("/model/official-keys")
    public ApiResponse<String> createOfficialKey(@Valid @RequestBody CreateModelKeyDto dto,
                                                 @RequestParam("operatorId") Long operatorId,
                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.createOfficialKey(
                new ModelCommands.CreateOfficialModelKeyCommand(
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

    @PatchMapping("/model/official-keys/{keyId}")
    public ApiResponse<String> updateOfficialKey(@PathVariable Long keyId,
                                                 @RequestBody UpdateModelKeyDto dto,
                                                 @RequestParam("operatorId") Long operatorId,
                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.updateOfficialKey(
                keyId,
                new ModelCommands.UpdateOfficialModelKeyCommand(
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

    @DeleteMapping("/model/official-keys/{keyId}")
    public ApiResponse<String> deleteOfficialKey(@PathVariable Long keyId,
                                                 @RequestParam("operatorId") Long operatorId,
                                                 @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.deleteOfficialKey(keyId, operatorId, traceId);
        return ApiResponse.success("deleted", traceId);
    }

    /**
     * 查询项目模型配置列表。
     * <p><b>业务目的：</b>返回项目下全部模型配置，供写作/Agent 场景显式选择调用配置。</p>
     * <p><b>流程主线：</b>读取项目业务ID -> 调用应用服务查询配置 -> 返回列表。</p>
     * <p><b>关键调用：</b>{@code modelApplicationService.listPolicies(projectId)}（底层复用历史策略实现）。</p>
     * <p><b>异常与分支：</b>项目不存在时返回业务异常。</p>
     * <p><b>副作用：</b>无持久化写入。</p>
     */
    @GetMapping({"/novels/{projectId}/model-configs", "/novels/{projectId}/model-policies"})
    public ApiResponse<List<ModelProjectPolicy>> listConfigs(@PathVariable Long projectId,
                                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(modelApplicationService.listPolicies(projectId), traceId);
    }

    /**
     * 创建项目模型配置。
     * <p><b>业务目的：</b>为项目新增可复用的模型调用配置（模型、密钥、采样参数等）。</p>
     * <p><b>流程主线：</b>校验入参 -> 组装 {@link ModelCommands.CreatePolicyCommand} -> 调用应用服务创建配置 -> 返回确认结果。</p>
     * <p><b>关键调用：</b>{@code modelApplicationService.createPolicy(...)}。</p>
     * <p><b>异常与分支：</b>模型不可用、密钥无效或参数越界时返回业务异常。</p>
     * <p><b>副作用：</b>新增配置记录，可能影响默认配置。</p>
     * <p><b>ID 语义：</b>projectId/providerModelId/userKeyId/officialKeyId 均为业务语义 ID。</p>
     */
    @PostMapping({"/novels/{projectId}/model-configs", "/novels/{projectId}/model-policies"})
    public ApiResponse<String> createConfig(@PathVariable Long projectId,
                                            @Valid @RequestBody CreateModelPolicyDto dto,
                                            @RequestParam("operatorId") Long operatorId,
                                            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.createPolicy(
                projectId,
                new ModelCommands.CreatePolicyCommand(
                        dto.getPolicyName(),
                        dto.getScene(),
                        dto.getProviderModelId(),
                        dto.getModelName(),
                        dto.getBaseUrl(),
                        dto.getUserKeyId(),
                        dto.getOfficialKeyId(),
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
     * 更新项目模型配置。
     * <p><b>业务目的：</b>调整既有配置的模型选择、参数配置与兜底策略。</p>
     * <p><b>流程主线：</b>读取配置标识 -> 组装 {@link ModelCommands.UpdatePolicyCommand} -> 调用应用服务更新 -> 返回确认结果。</p>
     * <p><b>关键调用：</b>{@code modelApplicationService.updatePolicy(...)}。</p>
     * <p><b>异常与分支：</b>配置不存在、默认配置约束冲突或权限不足时返回业务异常。</p>
     * <p><b>副作用：</b>更新配置，影响后续模型路由结果。</p>
     * <p><b>ID 语义：</b>projectId/configId/providerModelId/userKeyId/officialKeyId 均为业务语义 ID。</p>
     */
    @PutMapping({"/novels/{projectId}/model-configs/{configId}", "/novels/{projectId}/model-policies/{configId}"})
    public ApiResponse<String> updateConfig(@PathVariable Long projectId,
                                            @PathVariable Long configId,
                                            @RequestBody UpdateModelPolicyDto dto,
                                            @RequestParam("operatorId") Long operatorId,
                                            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.updatePolicy(
                projectId,
                configId,
                new ModelCommands.UpdatePolicyCommand(
                        dto.getPolicyName(),
                        dto.getScene(),
                        dto.getProviderModelId(),
                        dto.getModelName(),
                        dto.getBaseUrl(),
                        dto.getUserKeyId(),
                        dto.getOfficialKeyId(),
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
     * 删除项目模型配置。
     * <p><b>业务目的：</b>移除不再使用的配置，减少错误配置对生成链路的影响。</p>
     * <p><b>流程主线：</b>接收配置标识与操作者 -> 调用应用服务删除配置 -> 返回确认结果。</p>
     * <p><b>关键调用：</b>{@code modelApplicationService.deletePolicy(projectId, configId, operatorId, traceId)}。</p>
     * <p><b>异常与分支：</b>配置不存在、被强依赖或权限不足时返回业务异常。</p>
     * <p><b>副作用：</b>删除配置记录。</p>
     * <p><b>ID 语义：</b>projectId/configId 均为业务语义 ID。</p>
     */
    @DeleteMapping({"/novels/{projectId}/model-configs/{configId}", "/novels/{projectId}/model-policies/{configId}"})
    public ApiResponse<String> deleteConfig(@PathVariable Long projectId,
                                            @PathVariable Long configId,
                                            @RequestParam("operatorId") Long operatorId,
                                            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.deletePolicy(projectId, configId, operatorId, traceId);
        return ApiResponse.success("deleted", traceId);
    }

    /**
     * 将指定配置设为项目默认配置。
     * <p><b>业务目的：</b>明确未指定配置时的统一模型路由默认项。</p>
     * <p><b>流程主线：</b>接收项目与配置标识 -> 调用应用服务设置默认配置 -> 返回确认结果。</p>
     * <p><b>关键调用：</b>{@code modelApplicationService.setDefaultPolicy(projectId, configId, operatorId, traceId)}。</p>
     * <p><b>异常与分支：</b>配置不属于项目、不可用或权限不足时返回业务异常。</p>
     * <p><b>副作用：</b>更新项目默认策略指向。</p>
     * <p><b>ID 语义：</b>projectId/configId 均为业务语义 ID。</p>
     */
    @PostMapping({"/novels/{projectId}/model-configs/{configId}/set-default", "/novels/{projectId}/model-policies/{configId}/set-default"})
    public ApiResponse<String> setDefaultConfig(@PathVariable Long projectId,
                                                @PathVariable Long configId,
                                                @RequestParam("operatorId") Long operatorId,
                                                @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        modelApplicationService.setDefaultPolicy(projectId, configId, operatorId, traceId);
        return ApiResponse.success("updated", traceId);
    }
}
