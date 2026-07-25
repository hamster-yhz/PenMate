package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmProtocol;
import org.springframework.stereotype.Component;

@Component
public class OpenAiProviderChatClient extends AbstractOpenAiCompatibleProviderChatClient {

    @Override
    protected String resolveBaseUrl(String rawBaseUrl) {
        String baseUrl = super.resolveBaseUrl(rawBaseUrl);
        if (baseUrl == null) {
            return null;
        }
        String normalized = baseUrl.trim();
        if (normalized.endsWith("/chat/completions") || normalized.endsWith("/v1") || normalized.contains("/v1/")) {
            return normalized;
        }
        return ensureSuffixPath(normalized, "/v1");
    }

    @Override
    public boolean supports(String providerCode) {
        return "openai".equalsIgnoreCase(providerCode);
    }

    @Override
    public boolean supports(AgentLlmExecutionConfig executionConfig) {
        return supports(executionConfig == null ? null : executionConfig.providerCode())
                && AgentLlmProtocol.from(executionConfig.protocolCode()) != AgentLlmProtocol.OPENAI_RESPONSES;
    }
}

