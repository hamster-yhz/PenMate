package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.llm.AgentLlmCapabilities;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmInvocationCancelledException;
import com.penmate.backend.application.agent.llm.AgentLlmProtocol;
import com.penmate.backend.application.agent.llm.AgentLlmStreamEvent;
import com.penmate.backend.application.agent.llm.AgentLlmStreamObserver;
import com.penmate.backend.application.agent.llm.AgentLlmTransientException;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.llm.AgentReasoningPolicy;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmProviderItem;
import com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload;
import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletionException;

@Component
public class OpenAiResponsesProviderChatClient implements ProviderChatClient {

    private static final String PROTOCOL = AgentLlmProtocol.OPENAI_RESPONSES.name();

    private final HttpClient httpClient;

    public OpenAiResponsesProviderChatClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build());
    }

    OpenAiResponsesProviderChatClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public boolean supports(String providerCode) {
        return "openai".equalsIgnoreCase(providerCode);
    }

    @Override
    public boolean supports(AgentLlmExecutionConfig executionConfig) {
        return executionConfig != null
                && supports(executionConfig.providerCode())
                && AgentLlmProtocol.from(executionConfig.protocolCode()) == AgentLlmProtocol.OPENAI_RESPONSES;
    }

    @Override
    public AgentLlmCapabilities capabilities(AgentLlmExecutionConfig executionConfig) {
        return new AgentLlmCapabilities(AgentLlmProtocol.OPENAI_RESPONSES,
                true, true, true, true, true);
    }

    @Override
    public String generate(String prompt, AgentLlmExecutionConfig executionConfig) {
        return generateTurn(new AgentLlmTurnRequest(
                List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user(prompt)),
                List.of(), "none"), executionConfig).assistantText();
    }

    @Override
    public AgentLlmTurnResponse generateTurn(AgentLlmTurnRequest request,
                                             AgentLlmExecutionConfig executionConfig) {
        ResolvedRequest resolved = resolve(request, executionConfig);
        HttpResponse<String> response = sendBuffered(resolved, true);
        if (!isSuccess(response.statusCode())) {
            throw BusinessException.of("OpenAI Responses request failed: " + response.body());
        }
        return extractTurnResponse(response.body());
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public AgentLlmTurnResponse streamTurn(AgentLlmTurnRequest request,
                                           AgentLlmExecutionConfig executionConfig,
                                           AgentLlmStreamObserver observer) {
        ResolvedRequest resolved = resolve(request, executionConfig);
        HttpResponse<InputStream> response = sendStreaming(resolved, observer, true);
        if (!isSuccess(response.statusCode())) {
            try (InputStream body = response.body()) {
                throw BusinessException.of("OpenAI Responses stream failed: "
                        + new String(body.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException ex) {
                throw BusinessException.of("OpenAI Responses stream failed");
            }
        }
        try (InputStream body = response.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            observer.onCancellable(() -> closeQuietly(body));
            return readResponsesEventStream(reader, observer);
        } catch (IOException ex) {
            if (observer.isCancelled()) throw new AgentLlmInvocationCancelledException();
            throw new AgentLlmTransientException("OpenAI Responses stream read failed: " + ex.getMessage(), ex);
        }
    }

    String buildRequestBody(AgentLlmTurnRequest request,
                            AgentLlmExecutionConfig executionConfig,
                            boolean streaming,
                            boolean includeReasoning) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("model", executionConfig.modelName());
        String cacheKey = ProviderPromptCacheSupport.cacheKey(request, executionConfig);
        boolean explicitCache = cacheKey != null
                && ProviderPromptCacheSupport.useExplicitOpenAiBreakpoint(executionConfig);
        if (cacheKey != null) body.put("prompt_cache_key", cacheKey);
        if (explicitCache) body.put("prompt_cache_options", Map.of("mode", "explicit"));
        body.put("input", toResponsesInput(request.messages(), explicitCache));
        if (!request.tools().isEmpty()) {
            body.put("tools", request.tools().stream().map(this::toResponsesTool).toList());
            body.put("tool_choice", request.toolChoice());
        }
        AgentReasoningPolicy policy = executionConfig.reasoningPolicy();
        if (includeReasoning && policy != null && policy.requestsReasoning()) {
            LinkedHashMap<String, Object> reasoning = new LinkedHashMap<>();
            if ("adaptive".equals(policy.mode())) {
                throw BusinessException.of("OpenAI Responses does not support adaptive reasoning mode");
            }
            if (policy.disabled()) {
                reasoning.put("effort", "none");
            } else if (policy.explicitEffort()) {
                reasoning.put("effort", policy.effort());
            }
            if (policy.explicitMode()) reasoning.put("mode", policy.mode());
            if (policy.requestsSummary()) reasoning.put("summary", policy.summary());
            body.put("reasoning", reasoning);
        }
        body.put("store", false);
        body.put("max_output_tokens", executionConfig.maxOutputTokens());
        if (streaming) body.put("stream", true);
        return AgentJsonCodec.toJson(body);
    }

    AgentLlmTurnResponse readResponsesEventStream(BufferedReader reader,
                                                  AgentLlmStreamObserver observer) throws IOException {
        ResponsesStreamingState state = new ResponsesStreamingState();
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (observer.isCancelled()) throw new AgentLlmInvocationCancelledException();
            if (line.isBlank()) {
                if (!consumeEvent(data, state, observer)) break;
                data.setLength(0);
                continue;
            }
            if (line.startsWith("data:")) {
                if (!data.isEmpty()) data.append('\n');
                data.append(line.substring(5).trim());
            }
        }
        if (!data.isEmpty()) consumeEvent(data, state, observer);
        return state.toResponse(this, observer);
    }

    AgentLlmTurnResponse extractTurnResponse(String responseBody) {
        JSONObject root = AgentJsonCodec.parseObj(responseBody);
        JSONArray output = root.getJSONArray("output");
        if (output == null) output = new JSONArray();
        StringBuilder assistant = new StringBuilder();
        StringBuilder commentary = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        List<String> unclassifiedMessages = new ArrayList<>();
        List<AgentLlmToolCall> calls = new ArrayList<>();
        List<AgentLlmProviderItem> providerItems = new ArrayList<>();

        for (Object rawItem : output) {
            if (!(rawItem instanceof JSONObject item)) continue;
            providerItems.add(new AgentLlmProviderItem(PROTOCOL, item.toString()));
            String type = item.getStr("type", "");
            if ("reasoning".equals(type)) {
                appendSummary(item.getJSONArray("summary"), reasoning);
            } else if ("function_call".equals(type)) {
                calls.add(new AgentLlmToolCall(
                        firstNonBlank(item.getStr("call_id", null), item.getStr("id", null)),
                        item.getStr("name", null), item.getStr("arguments", "{}")));
            } else if ("message".equals(type)) {
                String text = outputItemText(item);
                switch (messageChannel(item)) {
                    case COMMENTARY -> commentary.append(text);
                    case FINAL -> assistant.append(text);
                    case UNKNOWN -> unclassifiedMessages.add(text);
                }
            }
        }
        StringBuilder unclassifiedTarget = calls.isEmpty() ? assistant : commentary;
        unclassifiedMessages.forEach(unclassifiedTarget::append);
        String finishReason = calls.isEmpty() ? "stop" : "tool_calls";
        return new AgentLlmTurnResponse(finishReason, assistant.toString(), calls, responseBody,
                extractUsage(root), commentary.toString(), reasoning.toString(), providerItems);
    }

    private boolean consumeEvent(StringBuilder data,
                                 ResponsesStreamingState state,
                                 AgentLlmStreamObserver observer) {
        if (data.isEmpty()) return true;
        String payload = data.toString().trim();
        if ("[DONE]".equals(payload)) return false;
        JSONObject event = AgentJsonCodec.parseObj(payload);
        String type = event.getStr("type", "");
        if ("error".equals(type) || "response.failed".equals(type)) {
            throw streamFailure(event, payload);
        }
        observer.onResponseStarted();

        if ("response.output_item.added".equals(type) || "response.output_item.done".equals(type)) {
            state.acceptOutputItem(event.getInt("output_index", state.outputItems.size()),
                    event.getJSONObject("item"));
        } else if ("response.output_text.delta".equals(type)) {
            state.acceptOutputText(event.getInt("output_index", 0), event.getStr("delta", ""), observer);
        } else if ("response.reasoning_summary_text.delta".equals(type)
                || "response.reasoning_summary.delta".equals(type)) {
            state.acceptReasoning(event.getStr("delta", ""), observer);
        } else if ("response.function_call_arguments.delta".equals(type)) {
            state.acceptToolArguments(event.getInt("output_index", 0), event.getStr("delta", ""));
        } else if ("response.function_call_arguments.done".equals(type)) {
            state.completeToolArguments(event.getInt("output_index", 0), event.getStr("arguments", ""));
        } else if ("response.completed".equals(type)) {
            state.completedResponse = event.getJSONObject("response");
        }
        return true;
    }

    private HttpResponse<String> sendBuffered(ResolvedRequest resolved, boolean allowReasoningFallback) {
        String requestBody = buildRequestBody(resolved.request, resolved.config, false, true);
        HttpResponse<String> response = sendString(resolved, requestBody);
        if (allowReasoningFallback && resolved.config.reasoningPolicy().allowsCompatibilityFallback()
                && response.statusCode() == 400 && requestBody.contains("\"reasoning\"")) {
            response = sendString(resolved, buildRequestBody(resolved.request, resolved.config, false, false));
        }
        return response;
    }

    private HttpResponse<String> sendString(ResolvedRequest resolved, String body) {
        try {
            return httpClient.send(baseRequest(resolved, body).build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw BusinessException.of("OpenAI Responses request interrupted");
        } catch (IOException ex) {
            throw BusinessException.of("OpenAI Responses request failed: " + ex.getMessage());
        }
    }

    private HttpResponse<InputStream> sendStreaming(ResolvedRequest resolved,
                                                    AgentLlmStreamObserver observer,
                                                    boolean allowReasoningFallback) {
        String body = buildRequestBody(resolved.request, resolved.config, true, true);
        HttpResponse<InputStream> response = sendInputStream(resolved, body, observer);
        if (allowReasoningFallback && resolved.config.reasoningPolicy().allowsCompatibilityFallback()
                && response.statusCode() == 400 && body.contains("\"reasoning\"")) {
            closeQuietly(response.body());
            response = sendInputStream(resolved,
                    buildRequestBody(resolved.request, resolved.config, true, false), observer);
        }
        return response;
    }

    private HttpResponse<InputStream> sendInputStream(ResolvedRequest resolved,
                                                      String body,
                                                      AgentLlmStreamObserver observer) {
        var future = httpClient.sendAsync(baseRequest(resolved, body)
                .header("Accept", "text/event-stream").build(), HttpResponse.BodyHandlers.ofInputStream());
        observer.onCancellable(() -> future.cancel(true));
        try {
            return future.join();
        } catch (CompletionException ex) {
            if (observer.isCancelled()) throw new AgentLlmInvocationCancelledException();
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new AgentLlmTransientException("OpenAI Responses stream failed: " + cause.getMessage(), cause);
        }
    }

    private RuntimeException streamFailure(JSONObject event, String payload) {
        JSONObject error = event.getJSONObject("error");
        if (error == null) {
            JSONObject response = event.getJSONObject("response");
            if (response != null) error = response.getJSONObject("error");
        }
        String errorType = error == null ? null : error.getStr("type", null);
        String errorCode = error == null ? null : error.getStr("code", null);
        String message = "OpenAI Responses stream failed: " + payload;
        if (isTransientProviderError(errorType) || isTransientProviderError(errorCode)) {
            return new AgentLlmTransientException(message);
        }
        return BusinessException.of(message);
    }

    private boolean isTransientProviderError(String value) {
        if (value == null || value.isBlank()) return false;
        return switch (value.trim().toLowerCase()) {
            case "upstream_error", "stream_read_error", "server_error", "rate_limit_error",
                    "overloaded_error", "timeout_error", "connection_error" -> true;
            default -> false;
        };
    }

    private HttpRequest.Builder baseRequest(ResolvedRequest resolved, String body) {
        return HttpRequest.newBuilder(URI.create(resolved.endpoint))
                .timeout(resolved.request.timeout())
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + resolved.config.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body));
    }

    private ResolvedRequest resolve(AgentLlmTurnRequest request, AgentLlmExecutionConfig config) {
        if (request == null || config == null || blank(config.apiKey()) || blank(config.modelName()) || blank(config.baseUrl())) {
            throw BusinessException.of("OpenAI Responses execution config is incomplete");
        }
        return new ResolvedRequest(request, config, responsesEndpoint(config.baseUrl()));
    }

    private String responsesEndpoint(String rawBaseUrl) {
        String baseUrl = rawBaseUrl.trim();
        while (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        if (baseUrl.endsWith("/responses")) return baseUrl;
        if (!baseUrl.endsWith("/v1") && !baseUrl.contains("/v1/")) baseUrl += "/v1";
        return baseUrl + "/responses";
    }

    private List<Object> toResponsesInput(List<AgentLlmMessage> messages, boolean explicitCache) {
        List<Object> input = new ArrayList<>();
        boolean cacheBreakpointAdded = false;
        for (AgentLlmMessage message : messages) {
            if (!message.providerItems().isEmpty()) {
                boolean added = false;
                for (AgentLlmProviderItem item : message.providerItems()) {
                    if (PROTOCOL.equalsIgnoreCase(item.protocolCode())) {
                        Object providerInput = toProviderInputItem(AgentJsonCodec.parseObj(item.payloadJson()));
                        if (providerInput != null) {
                            input.add(providerInput);
                            added = true;
                        }
                    }
                }
                if (added) continue;
            }
            switch (message.role()) {
                case SYSTEM, USER -> {
                    if (explicitCache && !cacheBreakpointAdded
                            && message.role() == com.penmate.backend.domain.agent.model.AgentLlmMessageRole.SYSTEM) {
                        input.add(Map.of(
                                "role", "system",
                                "content", List.of(Map.of(
                                        "type", "input_text",
                                        "text", message.content(),
                                        "prompt_cache_breakpoint", Map.of("mode", "explicit")))));
                        cacheBreakpointAdded = true;
                    } else {
                        input.add(Map.of("role", message.role().wireValue(), "content", message.content()));
                    }
                }
                case ASSISTANT -> {
                    if (!message.content().isBlank()) {
                        input.add(Map.of("role", "assistant", "content", message.content()));
                    }
                    for (AgentLlmToolCallPayload call : message.toolCalls()) {
                        input.add(Map.of(
                                "type", "function_call",
                                "call_id", call.id(),
                                "name", call.functionName(),
                                "arguments", call.argumentsJson()));
                    }
                }
                case TOOL -> input.add(Map.of(
                        "type", "function_call_output",
                        "call_id", message.toolCallId(),
                        "output", message.content()));
            }
        }
        return input;
    }

    private Object toProviderInputItem(JSONObject item) {
        String type = item.getStr("type", "");
        if ("reasoning".equals(type)) {
            JSONObject reasoning = AgentJsonCodec.parseObj(item.toString());
            reasoning.remove("phase");
            reasoning.remove("status");
            return reasoning;
        }
        if ("function_call".equals(type)) {
            return Map.of(
                    "type", "function_call",
                    "call_id", firstNonBlank(item.getStr("call_id", null), item.getStr("id", null)),
                    "name", item.getStr("name", ""),
                    "arguments", item.getStr("arguments", "{}"));
        }
        if ("message".equals(type)
                && !"commentary".equalsIgnoreCase(item.getStr("phase", ""))) {
            String text = outputItemText(item);
            if (!text.isBlank()) return Map.of("role", "assistant", "content", text);
        }
        return null;
    }

    private Map<String, Object> toResponsesTool(AgentLlmToolSchema schema) {
        JSONObject parameters = AgentJsonCodec.parseObj(schema.parametersJsonSchema());
        parameters.remove("oneOf");
        parameters.remove("anyOf");
        parameters.remove("allOf");
        parameters.remove("not");
        LinkedHashMap<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("name", schema.toolCode());
        tool.put("description", schema.description());
        tool.put("parameters", parameters);
        return tool;
    }

    private String outputItemText(JSONObject item) {
        JSONArray content = item.getJSONArray("content");
        if (content == null) return "";
        StringBuilder text = new StringBuilder();
        for (Object rawPart : content) {
            if (rawPart instanceof JSONObject part) text.append(part.getStr("text", ""));
        }
        return text.toString();
    }

    private static MessageChannel messageChannel(JSONObject item) {
        if (item == null) return MessageChannel.UNKNOWN;
        String phase = item.getStr("phase", "").trim();
        if ("commentary".equalsIgnoreCase(phase)) return MessageChannel.COMMENTARY;
        if ("final".equalsIgnoreCase(phase) || "final_answer".equalsIgnoreCase(phase)) {
            return MessageChannel.FINAL;
        }
        return MessageChannel.UNKNOWN;
    }

    private void appendSummary(JSONArray summary, StringBuilder target) {
        if (summary == null) return;
        for (Object rawPart : summary) {
            if (!(rawPart instanceof JSONObject part)) continue;
            String text = part.getStr("text", "");
            if (text.isBlank()) continue;
            if (!target.isEmpty()) target.append("\n\n");
            target.append(text);
        }
    }

    private LlmTokenUsage extractUsage(JSONObject root) {
        JSONObject usage = root == null ? null : root.getJSONObject("usage");
        if (usage == null) return LlmTokenUsage.ZERO;
        int input = intValue(usage, "input_tokens");
        int output = intValue(usage, "output_tokens");
        int total = intValue(usage, "total_tokens");
        JSONObject details = usage.getJSONObject("input_tokens_details");
        int cached = details == null ? 0 : intValue(details, "cached_tokens");
        int cacheWrite = details == null ? 0 : intValue(details, "cache_write_tokens");
        if (cacheWrite == 0) cacheWrite = intValue(usage, "cache_write_tokens");
        JSONObject outputDetails = usage.getJSONObject("output_tokens_details");
        int reasoning = outputDetails == null ? 0 : intValue(outputDetails, "reasoning_tokens");
        return new LlmTokenUsage(input, output, total, cached, cacheWrite, reasoning);
    }

    private int intValue(JSONObject object, String key) {
        Integer value = object.getInt(key);
        return value == null ? 0 : value;
    }

    private boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String firstNonBlank(String first, String second) {
        return !blank(first) ? first : second;
    }

    private void closeQuietly(InputStream body) {
        if (body == null) return;
        try {
            body.close();
        } catch (IOException ignored) {
            // Cancellation and fallback cleanup are best effort.
        }
    }

    private record ResolvedRequest(AgentLlmTurnRequest request,
                                   AgentLlmExecutionConfig config,
                                   String endpoint) {}

    private enum MessageChannel {
        COMMENTARY,
        FINAL,
        UNKNOWN
    }

    private static final class ResponsesStreamingState {
        private final StringBuilder assistant = new StringBuilder();
        private final StringBuilder commentary = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final Map<Integer, JSONObject> outputItems = new TreeMap<>();
        private final Map<Integer, StringBuilder> unclassifiedText = new TreeMap<>();
        private final Map<Integer, StreamingFunctionCall> toolCalls = new TreeMap<>();
        private JSONObject completedResponse;

        private void acceptOutputItem(int index, JSONObject item) {
            if (item == null) return;
            JSONObject previous = outputItems.get(index);
            if (item.getStr("phase", "").isBlank() && previous != null
                    && !previous.getStr("phase", "").isBlank()) {
                item.set("phase", previous.getStr("phase"));
            }
            outputItems.put(index, item);
            if (!"function_call".equals(item.getStr("type", ""))) return;
            StreamingFunctionCall call = toolCalls.computeIfAbsent(index, ignored -> new StreamingFunctionCall());
            call.id = first(item.getStr("call_id", null), item.getStr("id", null));
            call.name = item.getStr("name", call.name);
            String arguments = item.getStr("arguments", "");
            if (!arguments.isEmpty()) {
                call.arguments.setLength(0);
                call.arguments.append(arguments);
            }
        }

        private void acceptOutputText(int index, String text, AgentLlmStreamObserver observer) {
            if (text == null || text.isEmpty()) return;
            JSONObject item = outputItems.get(index);
            switch (messageChannel(item)) {
                case COMMENTARY -> appendCommentary(text, observer);
                case FINAL -> appendFinal(text, observer);
                case UNKNOWN -> unclassifiedText.computeIfAbsent(index, ignored -> new StringBuilder()).append(text);
            }
        }

        private void acceptReasoning(String text, AgentLlmStreamObserver observer) {
            if (text == null || text.isEmpty()) return;
            reasoning.append(text);
            observer.onEvent(new AgentLlmStreamEvent.ReasoningSummaryDelta(text));
        }

        private void acceptToolArguments(int index, String delta) {
            if (delta == null || delta.isEmpty()) return;
            toolCalls.computeIfAbsent(index, ignored -> new StreamingFunctionCall()).arguments.append(delta);
        }

        private void completeToolArguments(int index, String arguments) {
            if (arguments == null || arguments.isEmpty()) return;
            StreamingFunctionCall call = toolCalls.computeIfAbsent(index, ignored -> new StreamingFunctionCall());
            call.arguments.setLength(0);
            call.arguments.append(arguments);
        }

        private AgentLlmTurnResponse toResponse(OpenAiResponsesProviderChatClient client,
                                                AgentLlmStreamObserver observer) {
            if (completedResponse != null) {
                JSONObject enriched = AgentJsonCodec.parseObj(completedResponse.toString());
                JSONArray output = enriched.getJSONArray("output");
                if (output != null) {
                    for (int index = 0; index < output.size(); index++) {
                        JSONObject completedItem = output.getJSONObject(index);
                        JSONObject streamedItem = outputItems.get(index);
                        if (completedItem == null) continue;
                        if (streamedItem != null && completedItem.getStr("phase", "").isBlank()
                                && !streamedItem.getStr("phase", "").isBlank()) {
                            completedItem.set("phase", streamedItem.getStr("phase"));
                        }
                        outputItems.put(index, completedItem);
                    }
                }
                AgentLlmTurnResponse parsed = client.extractTurnResponse(enriched.toString());
                flushUnclassified(observer, !parsed.toolCalls().isEmpty());
                return new AgentLlmTurnResponse(parsed.finishReason(),
                        parsed.assistantText().isEmpty() ? assistant.toString() : parsed.assistantText(),
                        parsed.toolCalls(),
                        parsed.rawResponseJson(), parsed.tokenUsage(),
                        parsed.commentaryText().isEmpty() ? commentary.toString() : parsed.commentaryText(),
                        parsed.reasoningSummary().isEmpty() ? reasoning.toString() : parsed.reasoningSummary(),
                        parsed.providerItems());
            }
            List<AgentLlmToolCall> calls = toolCalls.entrySet().stream()
                    .map(entry -> new AgentLlmToolCall(
                            entry.getValue().id == null ? "call-" + entry.getKey() : entry.getValue().id,
                            entry.getValue().name,
                            entry.getValue().arguments.isEmpty() ? "{}" : entry.getValue().arguments.toString()))
                    .toList();
            flushUnclassified(observer, !calls.isEmpty());
            if (assistant.isEmpty() && commentary.isEmpty() && reasoning.isEmpty() && calls.isEmpty()) {
                throw BusinessException.of("OpenAI Responses stream completed without output");
            }
            return new AgentLlmTurnResponse(calls.isEmpty() ? "stop" : "tool_calls",
                    assistant.toString(), calls, "{}", LlmTokenUsage.ZERO,
                    commentary.toString(), reasoning.toString(), List.of());
        }

        private void flushUnclassified(AgentLlmStreamObserver observer, boolean hasToolCalls) {
            for (Map.Entry<Integer, StringBuilder> entry : unclassifiedText.entrySet()) {
                String text = entry.getValue().toString();
                MessageChannel channel = messageChannel(outputItems.get(entry.getKey()));
                if (channel == MessageChannel.UNKNOWN) {
                    channel = hasToolCalls ? MessageChannel.COMMENTARY : MessageChannel.FINAL;
                }
                if (channel == MessageChannel.COMMENTARY) appendCommentary(text, observer);
                else appendFinal(text, observer);
            }
            unclassifiedText.clear();
        }

        private void appendCommentary(String text, AgentLlmStreamObserver observer) {
            commentary.append(text);
            observer.onEvent(new AgentLlmStreamEvent.CommentaryDelta(text));
        }

        private void appendFinal(String text, AgentLlmStreamObserver observer) {
            assistant.append(text);
            observer.onEvent(new AgentLlmStreamEvent.OutputTextDelta(text));
        }

        private static String first(String first, String second) {
            return first != null && !first.isBlank() ? first : second;
        }
    }

    private static final class StreamingFunctionCall {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();
    }
}
