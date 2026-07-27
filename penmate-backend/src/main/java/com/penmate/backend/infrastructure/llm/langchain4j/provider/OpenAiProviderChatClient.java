package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmProtocol;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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

    @Override
    protected String outputTokenField() {
        return "max_completion_tokens";
    }

    @Override
    protected String buildTurnRequestBody(AgentLlmTurnRequest request, String modelName,
                                          AgentLlmExecutionConfig executionConfig, String endpoint) {
        JSONObject body = AgentJsonCodec.parseObj(super.buildTurnRequestBody(
                request, modelName, executionConfig, endpoint));
        String cacheKey = ProviderPromptCacheSupport.cacheKey(request, executionConfig);
        if (cacheKey == null) return body.toString();

        body.set("prompt_cache_key", cacheKey);
        if (ProviderPromptCacheSupport.useExplicitOpenAiBreakpoint(executionConfig)) {
            body.set("prompt_cache_options", Map.of("mode", "explicit"));
            addExplicitBreakpoint(body.getJSONArray("messages"));
        }
        return body.toString();
    }

    private void addExplicitBreakpoint(JSONArray messages) {
        if (messages == null) return;
        for (Object rawMessage : messages) {
            if (!(rawMessage instanceof JSONObject message)
                    || !"system".equalsIgnoreCase(message.getStr("role", ""))) continue;
            message.set("content", List.of(Map.of(
                    "type", "text",
                    "text", message.getStr("content", ""),
                    "prompt_cache_breakpoint", Map.of("mode", "explicit"))));
            return;
        }
    }
}

