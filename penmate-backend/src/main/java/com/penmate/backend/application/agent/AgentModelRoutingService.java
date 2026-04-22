package com.penmate.backend.application.agent;

import com.penmate.backend.application.model.BuiltinModelProviders;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.domain.model.model.ModelOfficialApiKey;
import com.penmate.backend.domain.model.model.ModelProjectPolicy;
import com.penmate.backend.domain.model.model.ModelProvider;
import com.penmate.backend.domain.model.model.ModelUserApiKey;
import com.penmate.backend.domain.model.repository.ModelRepository;
import com.penmate.backend.domain.shared.service.SecretCryptoService;
import com.penmate.backend.application.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 模型路由与执行配置解析服务。
 * <p>负责把“显式模型配置 + 模型定义 + 密钥来源”解析为一次可执行的模型调用配置，供 LLM 网关直接消费。</p>
 * <p>路由规则：仅使用任务显式传入的模型配置ID；任一关键依赖失效即直接失败，不走默认策略兜底。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentModelRoutingService {

    private final ModelRepository modelRepository;
    private final SecretCryptoService secretCryptoService;

    /**
     * 解析 Agent 任务的大模型执行配置。
     * <p><b>业务目的：</b>为一次生成任务选出可用模型、可用密钥和调用端点，确保调用链路具备可执行性。</p>
     * <p><b>流程主线：</b></p>
     * <ol>
     *   <li>按显式模型配置ID读取配置记录（当前复用历史策略表）。</li>
     *   <li>校验策略关联模型是否存在且启用。</li>
     *   <li>校验模型所属供应商是否存在且启用。</li>
     *   <li>若策略绑定用户密钥：校验密钥状态并解密明文 key。</li>
     *   <li>组装 {@link AgentLlmExecutionConfig} 返回给网关。</li>
     * </ol>
     * <p><b>关键调用：</b>{@code modelRepository.*} 负责读取策略/模型/密钥，{@code secretCryptoService.decrypt(...)} 负责密钥解密。</p>
     * <p><b>异常与分支：</b>任一关键对象缺失、被禁用或密钥不可用时，抛出业务异常中断任务，不做默认策略兜底。</p>
     * <p><b>副作用：</b>仅记录告警日志，不修改持久化状态。</p>
     *
     * @param projectId 项目ID
     * @param modelConfigId 模型配置ID（完全显式模式）
     * @param traceId 链路追踪ID，用于告警日志关联
     * @return 可直接用于模型调用的执行配置
     */
    public AgentLlmExecutionConfig resolveExecutionConfig(Long projectId, Long modelConfigId, String traceId) {
        // 步骤1：完全显式模式下仅按 modelConfigId 读取，不允许默认策略兜底。
        ModelProjectPolicy policy = modelRepository.findProjectPolicy(projectId, modelConfigId);
        // 无配置或配置未绑定模型时，直接失败。
        if (policy == null) {
            throw BusinessException.of("Model config not found");
        }

        // 步骤2：解析模型名。模型名始终以策略中的自由字符串为准，不再依赖供应商模型表。
        String resolvedModelName = policy.getModelName() == null ? "" : policy.getModelName().trim();
        if (resolvedModelName.isEmpty()) {
            throw BusinessException.of("Model name is required");
        }

        // 步骤3：供应商仅通过所选密钥反推，不再通过 providerModelId 绑定。
        Long resolvedProviderId = null;
        if (policy.getUserKeyId() != null) {
            ModelUserApiKey userKey = modelRepository.findUserKey(policy.getUserKeyId());
            if (userKey == null || isInactive(userKey.getStatus()) || userKey.getProviderId() == null) {
                throw BusinessException.of("User model key is unavailable");
            }
            resolvedProviderId = userKey.getProviderId();
        } else if (policy.getOfficialKeyId() != null) {
            ModelOfficialApiKey officialKey = modelRepository.findOfficialKey(policy.getOfficialKeyId());
            if (officialKey == null || isInactive(officialKey.getStatus()) || officialKey.getProviderId() == null) {
                throw BusinessException.of("Official model key is unavailable");
            }
            resolvedProviderId = officialKey.getProviderId();
        }
        if (resolvedProviderId == null) {
            throw BusinessException.of("Model provider cannot be resolved");
        }

        // 步骤4：供应商仅使用代码内置目录，避免访问已移除的旧表 model_providers。
        ModelProvider provider = BuiltinModelProviders.findById(resolvedProviderId).orElse(null);
        if (provider == null || isInactive(provider.getStatus())) {
            log.warn("模型策略引用的厂商不可用: projectId={}, policyId={}, providerId={}, traceId={}",
                    projectId,
                    policy.getId(),
                    resolvedProviderId,
                    traceId);
            throw BusinessException.of("Model provider is unavailable");
        }

        // 步骤5：解析本次调用密钥来源，仅允许 USER_KEY / OFFICIAL_KEY。
        Long userKeyId = policy.getUserKeyId();
        Long officialKeyId = policy.getOfficialKeyId();
        String plainApiKey = null;
        String keySource;
        if (userKeyId != null) {
            ModelUserApiKey userKey = modelRepository.findUserKey(userKeyId);
            if (userKey == null || isInactive(userKey.getStatus()) || userKey.getEncryptedApiKey() == null || userKey.getEncryptedApiKey().isBlank()) {
                log.warn("模型策略引用的用户密钥不可用: projectId={}, policyId={}, traceId={}", projectId, policy.getId(), traceId);
                throw BusinessException.of("User model key is unavailable");
            }
            plainApiKey = secretCryptoService.decrypt(userKey.getEncryptedApiKey());
            if (plainApiKey == null || plainApiKey.isBlank()) {
                log.warn("模型策略用户密钥解密后为空: projectId={}, policyId={}, traceId={}", projectId, policy.getId(), traceId);
                throw BusinessException.of("User model key decrypt failed");
            }
            keySource = "USER_KEY";
        } else if (officialKeyId != null) {
            ModelOfficialApiKey officialKey = modelRepository.findOfficialKey(officialKeyId);
            if (officialKey == null || isInactive(officialKey.getStatus()) || officialKey.getEncryptedApiKey() == null || officialKey.getEncryptedApiKey().isBlank()) {
                log.warn("模型策略引用的官方密钥不可用: projectId={}, policyId={}, traceId={}", projectId, policy.getId(), traceId);
                throw BusinessException.of("Official model key is unavailable");
            }
            plainApiKey = secretCryptoService.decrypt(officialKey.getEncryptedApiKey());
            if (plainApiKey == null || plainApiKey.isBlank()) {
                log.warn("模型策略官方密钥解密后为空: projectId={}, policyId={}, traceId={}", projectId, policy.getId(), traceId);
                throw BusinessException.of("Official model key decrypt failed");
            }
            keySource = "OFFICIAL_KEY";
        } else {
            log.warn("模型策略缺少可用密钥: projectId={}, policyId={}, traceId={}", projectId, policy.getId(), traceId);
            throw BusinessException.of("Model policy key is required");
        }

        // 步骤6：聚合路由结果，返回给模型网关执行实际调用。
        String resolvedBaseUrl = policy.getBaseUrl();
        if (resolvedBaseUrl == null || resolvedBaseUrl.isBlank()) {
            resolvedBaseUrl = provider.getBaseUrl();
        }
        return AgentLlmExecutionConfig.builder()
                .modelConfigId(policy.getId())
                .providerCode(provider.getCode())
                .baseUrl(resolvedBaseUrl)
                .apiKey(plainApiKey)
                .modelName(resolvedModelName)
                .keySource(keySource)
                .build();
    }

    /**
     * 判断状态是否为禁用态。
     *
     * @param status 状态值
     * @return true=禁用；false=可用或未知
     */
    private boolean isInactive(String status) {
        return status != null && "disabled".equalsIgnoreCase(status.trim());
    }
}

