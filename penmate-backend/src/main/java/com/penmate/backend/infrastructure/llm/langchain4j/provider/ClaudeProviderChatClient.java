package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.common.exception.BusinessException;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;

@Component
public class ClaudeProviderChatClient implements ProviderChatClient {

    @Override
    public String generate(String prompt, AgentLlmExecutionConfig executionConfig) {
        if (executionConfig == null) {
            throw BusinessException.of("LLM execution config is required");
        }

        String apiKey = executionConfig.apiKey();
        String baseUrl = executionConfig.baseUrl() == null ? null : executionConfig.baseUrl().trim();
        String modelName = executionConfig.modelName() == null ? null : executionConfig.modelName().trim();
        if (apiKey == null || apiKey.isBlank() || modelName == null || modelName.isBlank()) {
            throw BusinessException.of("LLM execution config is incomplete");
        }

        AnthropicChatModel.AnthropicChatModelBuilder builder = AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }

        ChatLanguageModel model = builder.build();
        return model.generate(prompt);
    }

    @Override
    public boolean supports(String providerCode) {
        return "claude".equalsIgnoreCase(providerCode);
    }
}

