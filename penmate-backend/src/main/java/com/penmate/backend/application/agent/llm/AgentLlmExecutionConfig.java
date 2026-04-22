package com.penmate.backend.application.agent.llm;

import lombok.Builder;

/**
 * Agent 单次模型调用执行配置。
 * <p>由模型路由服务从项目策略、模型定义和密钥信息聚合生成，供网关在一次生成任务中直接消费。</p>
 */
@Builder
public record AgentLlmExecutionConfig(
        /**
         * 命中的模型配置ID（显式传入）。
         * <p>注意：当前存储仍复用历史表主键，仅语义升级为“模型配置”。</p>
         */
        Long modelConfigId,
        /** 模型供应商编码，例如 openai。 */
        String providerCode,
        /** 供应商 API 基础地址，支持私有化网关地址。 */
        String baseUrl,
        /** 本次调用使用的明文 API Key；可能来自用户密钥或平台默认密钥。 */
        String apiKey,
        /** 目标模型名称/编码，例如 gpt-4o-mini。 */
        String modelName,
        /** 密钥来源标识，常见值：USER_KEY、OFFICIAL_KEY。 */
        String keySource) {
}

