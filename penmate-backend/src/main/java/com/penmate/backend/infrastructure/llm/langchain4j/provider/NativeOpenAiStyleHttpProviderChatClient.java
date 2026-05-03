package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于原生 HTTP 的 OpenAI 风格聊天调用实现。
 */
public abstract class NativeOpenAiStyleHttpProviderChatClient implements ProviderChatClient {

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
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw BusinessException.of("LLM request failed: " + ex.getMessage());
        } catch (IOException ex) {
            throw BusinessException.of("LLM request failed: " + ex.getMessage());
        }
    }

    @Override
    public AgentLlmTurnResponse generateTurn(AgentLlmTurnRequest turnRequest,
                                             AgentLlmExecutionConfig executionConfig) {
        if (executionConfig == null) {
            throw BusinessException.of("LLM execution config is required");
        }

        String apiKey = trim(executionConfig.apiKey());
        String modelName = trim(executionConfig.modelName());
        String endpoint = resolveChatCompletionsEndpoint(executionConfig.baseUrl());
        if (apiKey == null || modelName == null || endpoint == null) {
            throw BusinessException.of("LLM execution config is incomplete");
        }

        String requestBody = buildTurnRequestBody(turnRequest, modelName);
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
            return extractTurnResponse(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw BusinessException.of("LLM request failed: " + ex.getMessage());
        } catch (IOException ex) {
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
            return AgentJsonCodec.toJson(Map.of(
                    "model", modelName,
                    "messages", java.util.List.of(Map.of("role", "user", "content", prompt))
            ));
        } catch (Exception ex) {
            throw BusinessException.of("Failed to build LLM request");
        }
    }

    protected String buildTurnRequestBody(AgentLlmTurnRequest request, String modelName) {
        try {
            LinkedHashMap<String, Object> body = new LinkedHashMap<>();
            body.put("model", modelName);
            body.put("messages", request == null ? List.of() : request.messages());
            if (request != null && request.tools() != null && !request.tools().isEmpty()) {
                List<Map<String, Object>> tools = new ArrayList<>();
                for (AgentLlmToolSchema toolSchema : request.tools()) {
                    LinkedHashMap<String, Object> function = new LinkedHashMap<>();
                    function.put("name", toolSchema.toolCode());
                    function.put("description", toolSchema.description());
                    function.put("parameters", AgentJsonCodec.parseObj(toolSchema.parametersJsonSchema()));

                    LinkedHashMap<String, Object> tool = new LinkedHashMap<>();
                    tool.put("type", "function");
                    tool.put("function", function);
                    tools.add(tool);
                }
                body.put("tools", tools);
                body.put("tool_choice", request.toolChoice());
            }
            return AgentJsonCodec.toJson(body);
        } catch (Exception ex) {
            throw BusinessException.of("Failed to build LLM request");
        }
    }

    protected String extractContent(String responseBody) {
        try {
            JSONObject root = AgentJsonCodec.parseObj(responseBody);
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw BusinessException.of("LLM response content missing");
            }
            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice == null ? null : firstChoice.getJSONObject("message");
            String content = message == null ? null : message.getStr("content", null);
            if (content == null) {
                throw BusinessException.of("LLM response content missing");
            }
            return content;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BusinessException.of("Failed to parse LLM response");
        }
    }

    protected AgentLlmTurnResponse extractTurnResponse(String responseBody) {
        try {
            JSONObject root = AgentJsonCodec.parseObj(responseBody);
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw BusinessException.of("LLM response content missing");
            }
            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice == null ? null : firstChoice.getJSONObject("message");
            JSONArray toolCalls = message == null ? null : message.getJSONArray("tool_calls");
            List<AgentLlmToolCall> calls = new ArrayList<>();
            if (toolCalls != null) {
                for (int i = 0; i < toolCalls.size(); i++) {
                    JSONObject item = toolCalls.getJSONObject(i);
                    JSONObject function = item == null ? null : item.getJSONObject("function");
                    calls.add(new AgentLlmToolCall(
                            item == null ? null : item.getStr("id"),
                            function == null ? null : function.getStr("name"),
                            function == null ? "{}" : function.getStr("arguments", "{}")
                    ));
                }
            }
            return new AgentLlmTurnResponse(
                    firstChoice.getStr("finish_reason", "stop"),
                    message == null ? "" : message.getStr("content", ""),
                    calls,
                    responseBody
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
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
