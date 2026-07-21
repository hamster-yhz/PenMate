package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.llm.AgentLlmStreamObserver;
import com.penmate.backend.application.common.exception.BusinessException;

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

    /**
     * 调用供应商模型执行结构化单轮对话。
     */
    default AgentLlmTurnResponse generateTurn(AgentLlmTurnRequest request,
                                              AgentLlmExecutionConfig executionConfig) {
        throw BusinessException.of("LLM provider does not support structured turn generation");
    }

    default boolean supportsStreaming() {
        return false;
    }

    default AgentLlmTurnResponse streamTurn(AgentLlmTurnRequest request,
                                            AgentLlmExecutionConfig executionConfig,
                                            AgentLlmStreamObserver observer) {
        return generateTurn(request, executionConfig);
    }
}

