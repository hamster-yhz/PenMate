package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;

/**
 * 不同模型供应商调用策略。
 */
public interface ProviderChatClient {

    /**
     * 是否支持当前 provider。
     */
    boolean supports(String providerCode);

    /**
     * 调用供应商模型生成。
     */
    String generate(String prompt, AgentLlmExecutionConfig executionConfig);
}

