package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.common.exception.BusinessException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.springframework.stereotype.Component;

@Component
public class GeminiProviderChatClient extends AbstractOpenAiCompatibleProviderChatClient {

    @Override
    public String generate(String prompt, AgentLlmExecutionConfig executionConfig) {
        if (executionConfig == null) {
            throw BusinessException.of("LLM execution config is required");
        }

        String apiKey = executionConfig.apiKey();
        String modelName = executionConfig.modelName() == null ? null : executionConfig.modelName().trim();
        if (apiKey == null || apiKey.isBlank() || modelName == null || modelName.isBlank()) {
            throw BusinessException.of("LLM execution config is incomplete");
        }

        ChatLanguageModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
        return model.generate(prompt);
    }

    @Override
    protected String resolveBaseUrl(String rawBaseUrl) {
        String baseUrl = super.resolveBaseUrl(rawBaseUrl);
        if (baseUrl == null) {
            return null;
        }
        String normalized = baseUrl.trim();
        if (normalized.endsWith("/chat/completions") || normalized.contains("/openai/")) {
            return normalized;
        }
        return ensureSuffixPath(normalized, "/openai");
    }

    @Override
    public boolean supports(String providerCode) {
        return "gemini".equalsIgnoreCase(providerCode);
    }
}

