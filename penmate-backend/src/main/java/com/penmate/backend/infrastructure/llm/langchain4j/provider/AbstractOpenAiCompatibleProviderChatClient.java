package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.common.exception.BusinessException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * OpenAI 兼容协议供应商基础实现。
 */
public abstract class AbstractOpenAiCompatibleProviderChatClient extends NativeOpenAiStyleHttpProviderChatClient {

    @Override
    public String generate(String prompt, AgentLlmExecutionConfig executionConfig) {
        if (executionConfig == null) {
            throw BusinessException.of("LLM execution config is required");
        }
        String baseUrl = resolveBaseUrl(executionConfig.baseUrl());
        String apiKey = executionConfig.apiKey();
        String modelName = resolveModelName(executionConfig.modelName());
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank() || modelName == null || modelName.isBlank()) {
            throw BusinessException.of("LLM execution config is incomplete");
        }
        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
        return model.generate(prompt);
    }

    protected String resolveBaseUrl(String rawBaseUrl) {
        if (rawBaseUrl == null) {
            return null;
        }
        return rawBaseUrl.trim();
    }

    protected String resolveModelName(String rawModelName) {
        return rawModelName == null ? null : rawModelName.trim();
    }

    @Override
    protected String resolveChatCompletionsEndpoint(String rawBaseUrl) {
        String baseUrl = resolveBaseUrl(rawBaseUrl);
        if (baseUrl == null) {
            return null;
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return super.resolveChatCompletionsEndpoint(normalized);
    }

    protected String ensureSuffixPath(String baseUrl, String suffixPath) {
        if (baseUrl == null) {
            return null;
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.toLowerCase().endsWith(suffixPath.toLowerCase())) {
            return normalized;
        }
        return normalized + suffixPath;
    }
}

