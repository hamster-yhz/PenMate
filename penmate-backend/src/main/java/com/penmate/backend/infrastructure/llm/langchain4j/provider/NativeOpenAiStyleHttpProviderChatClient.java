package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONNull;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmInvocationCancelledException;
import com.penmate.backend.application.agent.llm.AgentLlmStreamObserver;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletionException;

/**
 * 鍩轰簬鍘熺敓 HTTP 鐨?OpenAI 椋庢牸鑱婂ぉ璋冪敤瀹炵幇銆?
 */
@Slf4j
public abstract class NativeOpenAiStyleHttpProviderChatClient implements ProviderChatClient {

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
                .timeout(turnRequest.timeout())
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

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public AgentLlmTurnResponse streamTurn(AgentLlmTurnRequest turnRequest,
                                           AgentLlmExecutionConfig executionConfig,
                                           AgentLlmStreamObserver observer) {
        if (executionConfig == null) {
            throw BusinessException.of("LLM execution config is required");
        }
        String apiKey = trim(executionConfig.apiKey());
        String modelName = trim(executionConfig.modelName());
        String endpoint = resolveChatCompletionsEndpoint(executionConfig.baseUrl());
        if (apiKey == null || modelName == null || endpoint == null) {
            throw BusinessException.of("LLM execution config is incomplete");
        }

        String requestBody = buildStreamingTurnRequestBody(turnRequest, modelName, endpoint);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofMinutes(5))
                .header("Accept", "text/event-stream")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        var responseFuture = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        observer.onCancellable(() -> responseFuture.cancel(true));

        try {
            HttpResponse<InputStream> response = responseFuture.join();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (InputStream body = response.body()) {
                    String errorBody = new String(body.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    throw BusinessException.of("LLM stream request failed: " + errorBody);
                }
            }
            try (InputStream body = response.body();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(
                         body, java.nio.charset.StandardCharsets.UTF_8))) {
                observer.onCancellable(() -> closeQuietly(body));
                return readOpenAiEventStream(reader, observer);
            }
        } catch (AgentLlmInvocationCancelledException ex) {
            throw ex;
        } catch (CompletionException ex) {
            if (observer.isCancelled()) throw new AgentLlmInvocationCancelledException();
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw BusinessException.of("LLM stream request failed: " + cause.getMessage());
        } catch (IOException ex) {
            if (observer.isCancelled()) throw new AgentLlmInvocationCancelledException();
            throw BusinessException.of("LLM stream read failed: " + ex.getMessage());
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
                body.put("tool_choice", request.toolChoice());
            }
            return AgentJsonCodec.toJson(body);
        } catch (Exception ex) {
            throw BusinessException.of("Failed to build LLM request");
        }
    }

    protected String buildStreamingTurnRequestBody(AgentLlmTurnRequest request,
                                                    String modelName,
                                                    String endpoint) {
        JSONObject body = AgentJsonCodec.parseObj(buildTurnRequestBody(request, modelName, endpoint));
        body.set("stream", true);
        return body.toString();
    }

    AgentLlmTurnResponse readOpenAiEventStream(BufferedReader reader,
                                               AgentLlmStreamObserver observer) throws IOException {
        StreamingTurnState state = new StreamingTurnState();
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (observer.isCancelled()) throw new AgentLlmInvocationCancelledException();
            if (line.isBlank()) {
                if (!consumeSseData(data, state, observer)) break;
                data.setLength(0);
                continue;
            }
            if (line.startsWith("data:")) {
                if (!data.isEmpty()) data.append('\n');
                data.append(line.substring(5).trim());
            }
        }
        if (!data.isEmpty()) consumeSseData(data, state, observer);
        return state.toResponse();
    }

    private boolean consumeSseData(StringBuilder data,
                                   StreamingTurnState state,
                                   AgentLlmStreamObserver observer) {
        if (data.isEmpty()) return true;
        String payload = data.toString().trim();
        if ("[DONE]".equals(payload)) return false;
        observer.onResponseStarted();
        JSONObject root = AgentJsonCodec.parseObj(payload);
        JSONArray choices = root.getJSONArray("choices");
        if (choices != null && !choices.isEmpty()) {
            JSONObject choice = choices.getJSONObject(0);
            if (choice != null) {
                JSONObject delta = choice.getJSONObject("delta");
                if (delta != null) {
                    String text = streamingText(delta.get("content"));
                    if (text != null && !text.isEmpty()) {
                        state.assistantText.append(text);
                        observer.onTextDelta(text);
                    }
                    state.appendToolDeltas(delta.getJSONArray("tool_calls"));
                }
                if (state.assistantText.isEmpty() && delta == null) {
                    JSONObject message = choice.getJSONObject("message");
                    String text = message == null ? streamingText(choice.get("text"))
                            : streamingText(message.get("content"));
                    state.appendText(text, observer);
                }
                String finishReason = choice.getStr("finish_reason", null);
                if (finishReason != null && !finishReason.isBlank()) state.finishReason = finishReason;
            }
        }
        String eventType = root.getStr("type", "");
        if ("response.output_text.delta".equals(eventType)) {
            state.appendText(streamingText(root.get("delta")), observer);
        } else if ("content_block_delta".equals(eventType)) {
            JSONObject delta = root.getJSONObject("delta");
            state.appendText(delta == null ? "" : streamingText(delta.get("text")), observer);
        } else if (state.assistantText.isEmpty() && "response.completed".equals(eventType)) {
            state.appendText(responseOutputText(root.getJSONObject("response")), observer);
        }
        state.tokenUsage = extractTokenUsage(root);
        return true;
    }

    private String streamingText(Object value) {
        if (value == null || value instanceof JSONNull) return "";
        if (value instanceof CharSequence text) return text.toString();
        if (value instanceof JSONArray items) {
            StringBuilder result = new StringBuilder();
            for (Object item : items) {
                if (item instanceof JSONObject object) {
                    String text = streamingText(object.get("text"));
                    if (text.isEmpty()) text = streamingText(object.get("content"));
                    result.append(text);
                } else {
                    result.append(streamingText(item));
                }
            }
            return result.toString();
        }
        if (value instanceof JSONObject object) {
            String text = streamingText(object.get("text"));
            return text.isEmpty() ? streamingText(object.get("content")) : text;
        }
        return "";
    }

    private String responseOutputText(JSONObject response) {
        if (response == null) return "";
        JSONArray output = response.getJSONArray("output");
        if (output == null) return "";
        StringBuilder result = new StringBuilder();
        for (Object item : output) {
            if (!(item instanceof JSONObject outputItem)) continue;
            result.append(streamingText(outputItem.get("content")));
        }
        return result.toString();
    }

    private void closeQuietly(InputStream body) {
        try {
            body.close();
        } catch (IOException ignored) {
            // Cancellation is best effort.
        }
    }

    private static final class StreamingTurnState {
        private final StringBuilder assistantText = new StringBuilder();
        private final Map<Integer, StreamingToolCall> toolCalls = new TreeMap<>();
        private String finishReason = "stop";
        private LlmTokenUsage tokenUsage = LlmTokenUsage.ZERO;

        private void appendText(String text, AgentLlmStreamObserver observer) {
            if (text == null || text.isEmpty()) return;
            assistantText.append(text);
            observer.onTextDelta(text);
        }

        private void appendToolDeltas(JSONArray deltas) {
            if (deltas == null) return;
            for (int i = 0; i < deltas.size(); i++) {
                JSONObject delta = deltas.getJSONObject(i);
                if (delta == null) continue;
                int index = delta.getInt("index", i);
                StreamingToolCall call = toolCalls.computeIfAbsent(index, ignored -> new StreamingToolCall());
                String id = delta.getStr("id", null);
                if (id != null && !id.isBlank()) call.id = id;
                JSONObject function = delta.getJSONObject("function");
                if (function == null) continue;
                String name = function.getStr("name", null);
                if (name != null && !name.isBlank()) call.name = name;
                String arguments = function.getStr("arguments", null);
                if (arguments != null) call.arguments.append(arguments);
            }
        }

        private AgentLlmTurnResponse toResponse() {
            List<AgentLlmToolCall> calls = toolCalls.entrySet().stream()
                    .map(entry -> new AgentLlmToolCall(
                            entry.getValue().id == null ? "tool-call-" + entry.getKey() : entry.getValue().id,
                            entry.getValue().name,
                            entry.getValue().arguments.toString()))
                    .toList();
            String resolvedFinishReason = calls.isEmpty() ? finishReason : "tool_calls";
            if (calls.isEmpty() && assistantText.isEmpty()) {
                throw BusinessException.of("LLM stream completed without assistant content");
            }
            return new AgentLlmTurnResponse(
                    resolvedFinishReason, assistantText.toString(), calls, "{}", tokenUsage);
        }
    }

    private static final class StreamingToolCall {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();
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
            if (toolCallsNode instanceof JSONNull) { toolCallsNode = null; }
            toolCallsType = describeJsonValueType(toolCallsNode);
            if (toolCallsNode != null && !(toolCallsNode instanceof JSONArray)) {
                throw new IllegalStateException("LLM response tool_calls is not a json array");
            }
            JSONArray toolCalls = (toolCallsNode != null && toolCallsNode instanceof JSONArray) ? (JSONArray) toolCallsNode : null;
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
        JSONObject promptDetails = usage.getJSONObject("prompt_tokens_details");
        Integer cachedTokens = promptDetails == null ? null : promptDetails.getInt("cached_tokens");
        if (cachedTokens == null) cachedTokens = usage.getInt("cache_read_input_tokens");
        Integer cacheCreationTokens = usage.getInt("cache_creation_input_tokens");
        return new LlmTokenUsage(
                promptTokens == null ? 0 : promptTokens,
                completionTokens == null ? 0 : completionTokens,
                totalTokens == null ? 0 : totalTokens,
                cachedTokens == null ? 0 : cachedTokens,
                cacheCreationTokens == null ? 0 : cacheCreationTokens
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
