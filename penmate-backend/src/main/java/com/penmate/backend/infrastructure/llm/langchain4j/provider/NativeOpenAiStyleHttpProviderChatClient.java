package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.common.exception.BusinessException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 基于原生 HTTP 的 OpenAI 风格聊天调用实现。
 */
public abstract class NativeOpenAiStyleHttpProviderChatClient implements ProviderChatClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public String generate(String prompt, AgentLlmExecutionConfig executionConfig) {
        if (executionConfig == null) {
            throw BusinessException.of("LLM execution config is required");
        }

        String apiKey = trim(executionConfig.apiKey());
        String modelName = trim(executionConfig.modelName());
        String endpoint = resolveChatCompletionsEndpoint(executionConfig.baseUrl());
        if (apiKey == null || modelName == null || endpoint == null) {
            throw BusinessException.of("LLM execution config is incomplete");
        }

        String requestBody = buildRequestBody(prompt, modelName);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw BusinessException.of("LLM request failed: " + response.body());
            }
            return extractContent(response.body());
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw BusinessException.of("LLM request failed: " + ex.getMessage());
        }
    }

    protected String resolveChatCompletionsEndpoint(String rawBaseUrl) {
        String baseUrl = trim(rawBaseUrl);
        if (baseUrl == null) {
            return null;
        }
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (baseUrl.endsWith("/chat/completions")) {
            return baseUrl;
        }
        return baseUrl + "/chat/completions";
    }

    protected String buildRequestBody(String prompt, String modelName) {
        try {
            return OBJECT_MAPPER.writeValueAsString(Map.of(
                    "model", modelName,
                    "messages", new Object[]{Map.of("role", "user", "content", prompt)}
            ));
        } catch (IOException ex) {
            throw BusinessException.of("Failed to build LLM request");
        }
    }

    protected String extractContent(String responseBody) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw BusinessException.of("LLM response content missing");
            }
            return content.asText();
        } catch (IOException ex) {
            throw BusinessException.of("Failed to parse LLM response");
        }
    }

    protected String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
