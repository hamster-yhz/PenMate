package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.llm.LlmTokenUsage;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

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
import java.util.Objects;

/**
 * 基于原生 HTTP 的 OpenAI 风格聊天调用实现。
 */
@Slf4j
public abstract class NativeOpenAiStyleHttpProviderChatClient implements ProviderChatClient {

    private static final String STRUCTURED_PREFLIGHT_TOOL_CODE = "submit_preflight_decision";

    @Autowired
    private AgentLlmMessagePayloadMapper agentLlmMessagePayloadMapper = new AgentLlmMessagePayloadMapper();

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

        String requestBody = buildTurnRequestBody(turnRequest, modelName, endpoint);
        log.info("llm.turn.request.dispatch: endpoint={}, modelName={}, messageCount={}, toolCount={}",
                endpoint,
                modelName,
                turnRequest == null || turnRequest.messages() == null ? 0 : turnRequest.messages().size(),
                turnRequest == null || turnRequest.tools() == null ? 0 : turnRequest.tools().size());
        log.debug("llm.turn.request.payload: endpoint={}, requestBodySnippet={}",
                endpoint,
                abbreviateForLog(requestBody));
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("llm.turn.response.raw: endpoint={}, statusCode={}, responseBodySnippet={}",
                    endpoint,
                    response.statusCode(),
                    abbreviateForLog(response.body()));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw BusinessException.of("LLM request failed: " + response.body());
            }
            try {
                return extractTurnResponse(response.body());
            } catch (BusinessException ex) {
                log.warn("llm.turn.response.parse.context: endpoint={}, statusCode={}, requestBodySnippet={}, responseBodySnippet={}",
                        endpoint,
                        response.statusCode(),
                        abbreviateForLog(requestBody),
                        abbreviateForLog(response.body()));
                throw ex;
            }
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
        return buildTurnRequestBody(request, modelName, null);
    }

    protected String buildTurnRequestBody(AgentLlmTurnRequest request, String modelName, String endpoint) {
        try {
            LinkedHashMap<String, Object> body = new LinkedHashMap<>();
            body.put("model", modelName);
            body.put("messages", agentLlmMessagePayloadMapper.toProviderMessages(request == null ? List.of() : request.messages()));
            if (shouldUseJsonSchemaResponseFormat(request, endpoint)) {
                AgentLlmToolSchema structuredOutputTool = request.tools().get(0);
                body.put("response_format", buildJsonSchemaResponseFormat(structuredOutputTool));
                return AgentJsonCodec.toJson(body);
            }
            if (request != null && request.tools() != null && !request.tools().isEmpty()) {
                List<Map<String, Object>> tools = new ArrayList<>();
                for (AgentLlmToolSchema toolSchema : request.tools()) {
                    LinkedHashMap<String, Object> function = new LinkedHashMap<>();
                    function.put("name", toolSchema.toolCode());
                    function.put("description", toolSchema.description());
                    function.put("parameters", sanitizeTopLevelFunctionParametersSchema(toolSchema.parametersJsonSchema()));

                    LinkedHashMap<String, Object> tool = new LinkedHashMap<>();
                    tool.put("type", "function");
                    tool.put("function", function);
                    tools.add(tool);
                }
                body.put("tools", tools);
                body.put("tool_choice", buildToolChoicePayload(request));
            }
            return AgentJsonCodec.toJson(body);
        } catch (Exception ex) {
            throw BusinessException.of("Failed to build LLM request");
        }
    }

    protected boolean supportsJsonSchemaResponseFormat() {
        return false;
    }

    protected boolean supportsJsonSchemaResponseFormat(String endpoint) {
        return supportsJsonSchemaResponseFormat();
    }

    private boolean shouldUseJsonSchemaResponseFormat(AgentLlmTurnRequest request, String endpoint) {
        return supportsJsonSchemaResponseFormat(endpoint) && isStructuredPreflightDecisionRequest(request);
    }

    private Object buildToolChoicePayload(AgentLlmTurnRequest request) {
        if (isStructuredPreflightDecisionRequest(request)) {
            return Map.of(
                    "type", "function",
                    "function", Map.of("name", request.tools().get(0).toolCode())
            );
        }
        return request.toolChoice();
    }

    private boolean isStructuredPreflightDecisionRequest(AgentLlmTurnRequest request) {
        if (request == null || request.tools() == null || request.tools().size() != 1) {
            return false;
        }
        if (!"required".equalsIgnoreCase(trim(request.toolChoice()))) {
            return false;
        }
        AgentLlmToolSchema toolSchema = request.tools().get(0);
        return toolSchema != null && Objects.equals(STRUCTURED_PREFLIGHT_TOOL_CODE, toolSchema.toolCode());
    }

    private Map<String, Object> buildJsonSchemaResponseFormat(AgentLlmToolSchema toolSchema) {
        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", toolSchema.toolCode(),
                        "strict", true,
                        "schema", AgentJsonCodec.parseObj(toolSchema.parametersJsonSchema())
                )
        );
    }

    private JSONObject sanitizeTopLevelFunctionParametersSchema(String parametersJsonSchema) {
        JSONObject schema = AgentJsonCodec.parseObj(parametersJsonSchema);
        schema.remove("oneOf");
        schema.remove("anyOf");
        schema.remove("allOf");
        schema.remove("enum");
        schema.remove("not");
        return schema;
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
        boolean choicesPresent = false;
        Integer choiceCount = null;
        boolean firstChoicePresent = false;
        String firstChoiceType = "null";
        boolean messagePresent = false;
        String messageType = "null";
        boolean toolCallsPresent = false;
        String toolCallsType = "null";
        Integer toolCallCount = null;
        String finishReason = null;
        boolean contentPresent = false;
        int contentLength = 0;
        try {
            JSONObject root = AgentJsonCodec.parseObj(responseBody);
            JSONArray choices = root.getJSONArray("choices");
            choicesPresent = choices != null;
            choiceCount = choices == null ? null : choices.size();
            if (choices == null || choices.isEmpty()) {
                log.warn("llm.turn.response.choices.missing: rawResponseSnippet={}", abbreviateForLog(responseBody));
                throw BusinessException.of("LLM response content missing");
            }

            Object firstChoiceNode = choices.get(0);
            firstChoicePresent = firstChoiceNode != null;
            firstChoiceType = describeJsonValueType(firstChoiceNode);
            if (!(firstChoiceNode instanceof JSONObject firstChoice)) {
                throw new IllegalStateException("LLM response first choice is not a json object");
            }

            finishReason = firstChoice.getStr("finish_reason", "stop");
            Object messageNode = firstChoice.get("message");
            messagePresent = messageNode != null;
            messageType = describeJsonValueType(messageNode);
            if (!(messageNode instanceof JSONObject message)) {
                throw new IllegalStateException("LLM response message is missing or not a json object");
            }

            contentPresent = message.containsKey("content");
            String content = message.getStr("content", "");
            contentLength = content == null ? 0 : content.length();

            LlmTokenUsage tokenUsage = extractTokenUsage(root);
            Object toolCallsNode = message.get("tool_calls");
            toolCallsPresent = toolCallsNode != null;
            toolCallsType = describeJsonValueType(toolCallsNode);
            if (toolCallsNode != null && !(toolCallsNode instanceof JSONArray)) {
                throw new IllegalStateException("LLM response tool_calls is not a json array");
            }
            JSONArray toolCalls = toolCallsNode instanceof JSONArray ? (JSONArray) toolCallsNode : null;
            toolCallCount = toolCalls == null ? 0 : toolCalls.size();

            log.info("llm.turn.response.structure: finishReason={}, messagePresent={}, messageType={}, contentPresent={}, contentLength={}, toolCallsPresent={}, toolCallsType={}, toolCallCount={}",
                    finishReason,
                    messagePresent,
                    messageType,
                    contentPresent,
                    contentLength,
                    toolCallsPresent,
                    toolCallsType,
                    toolCallCount);
            boolean hasToolCallsFinishReason = "tool_calls".equalsIgnoreCase(finishReason);
            boolean hasExpectedToolCallsPayload = hasToolCallsFinishReason && toolCalls != null && !toolCalls.isEmpty();
            if (!contentPresent && !hasExpectedToolCallsPayload) {
                log.warn("llm.turn.response.content.missing: finishReason={}, toolCallsPresent={}, toolCallCount={}, rawResponseSnippet={}",
                        finishReason,
                        toolCallsPresent,
                        toolCallCount,
                        abbreviateForLog(responseBody));
            }
            if (hasToolCallsFinishReason && (toolCalls == null || toolCalls.isEmpty())) {
                log.warn("llm.turn.response.tool_calls.expected_but_missing: finishReason={}, rawResponseSnippet={}",
                        finishReason,
                        abbreviateForLog(responseBody));
            }

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
                    finishReason,
                    content == null ? "" : content,
                    calls,
                    responseBody,
                    tokenUsage
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("llm.turn.response.parse.failed: choicesPresent={}, choiceCount={}, firstChoicePresent={}, firstChoiceType={}, messagePresent={}, messageType={}, toolCallsPresent={}, toolCallsType={}, toolCallCount={}, finishReason={}, rawResponseSnippet={}",
                    choicesPresent,
                    choiceCount,
                    firstChoicePresent,
                    firstChoiceType,
                    messagePresent,
                    messageType,
                    toolCallsPresent,
                    toolCallsType,
                    toolCallCount,
                    finishReason,
                    abbreviateForLog(responseBody),
                    ex);
            throw BusinessException.of("Failed to parse LLM response");
        }
    }

    private LlmTokenUsage extractTokenUsage(JSONObject root) {
        if (root == null) {
            return LlmTokenUsage.ZERO;
        }
        JSONObject usage = root.getJSONObject("usage");
        if (usage == null) {
            return LlmTokenUsage.ZERO;
        }
        Integer promptTokens = usage.getInt("prompt_tokens");
        Integer completionTokens = usage.getInt("completion_tokens");
        Integer totalTokens = usage.getInt("total_tokens");
        return new LlmTokenUsage(
                promptTokens == null ? 0 : promptTokens,
                completionTokens == null ? 0 : completionTokens,
                totalTokens == null ? 0 : totalTokens
        );
    }

    private String abbreviateForLog(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\r", "\\r").replace("\n", "\\n");
        if (normalized.length() <= 4000) {
            return normalized;
        }
        return normalized.substring(0, 4000) + "...(truncated,length=" + normalized.length() + ")";
    }

    private String describeJsonValueType(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof JSONObject) {
            return "object";
        }
        if (value instanceof JSONArray) {
            return "array";
        }
        if (value instanceof CharSequence) {
            return "string";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        return value.getClass().getSimpleName();
    }

    protected String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
