package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.llm.AgentLlmCapabilities;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmProtocol;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmProviderItem;
import com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload;
import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ClaudeProviderChatClient implements ProviderChatClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private final HttpClient httpClient;

    public ClaudeProviderChatClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build());
    }

    ClaudeProviderChatClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public String generate(String prompt, AgentLlmExecutionConfig executionConfig) {
        return generateTurn(new AgentLlmTurnRequest(
                List.of(AgentLlmMessage.user(prompt)), List.of(), "none"), executionConfig).assistantText();
    }

    @Override
    public AgentLlmTurnResponse generateTurn(AgentLlmTurnRequest request,
                                             AgentLlmExecutionConfig executionConfig) {
        ResolvedRequest resolved = resolve(request, executionConfig);
        String body = buildRequestBody(request, executionConfig);
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(resolved.endpoint()))
                .timeout(request.timeout())
                .header("Content-Type", "application/json")
                .header("x-api-key", executionConfig.apiKey())
                .header("anthropic-version", ANTHROPIC_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw BusinessException.of("Claude Messages request failed: " + response.body());
            }
            return extractTurnResponse(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw BusinessException.of("Claude Messages request interrupted");
        } catch (IOException ex) {
            throw BusinessException.of("Claude Messages request failed: " + ex.getMessage());
        }
    }

    @Override
    public boolean supports(String providerCode) {
        return "claude".equalsIgnoreCase(providerCode);
    }

    @Override
    public AgentLlmCapabilities capabilities(AgentLlmExecutionConfig executionConfig) {
        return new AgentLlmCapabilities(AgentLlmProtocol.ANTHROPIC_MESSAGES,
                false, true, false, false, false);
    }

    String buildRequestBody(AgentLlmTurnRequest request, AgentLlmExecutionConfig config) {
        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.modelName());
        body.put("max_tokens", config.maxOutputTokens());

        List<Map<String, Object>> system = toSystemBlocks(request.messages());
        if (!system.isEmpty()) body.put("system", system);
        List<Map<String, Object>> messages = toMessages(request.messages());
        body.put("messages", messages);
        applyReasoningControls(body, config);

        if (!request.tools().isEmpty() && !"none".equalsIgnoreCase(request.toolChoice())) {
            List<Map<String, Object>> tools = request.tools().stream().map(this::toTool).toList();
            if (system.isEmpty()) {
                tools.get(tools.size() - 1).put("cache_control", Map.of("type", "ephemeral"));
            }
            body.put("tools", tools);
            body.put("tool_choice", Map.of("type",
                    "required".equalsIgnoreCase(request.toolChoice()) ? "any" : "auto"));
        } else if (system.isEmpty()) {
            addMessageCacheBreakpoint(messages);
        }
        return AgentJsonCodec.toJson(body);
    }

    AgentLlmTurnResponse extractTurnResponse(String responseBody) {
        JSONObject root = AgentJsonCodec.parseObj(responseBody);
        JSONArray content = root.getJSONArray("content");
        StringBuilder text = new StringBuilder();
        List<AgentLlmToolCall> toolCalls = new ArrayList<>();
        List<AgentLlmProviderItem> providerItems = new ArrayList<>();
        if (content != null) {
            for (Object rawBlock : content) {
                if (!(rawBlock instanceof JSONObject block)) continue;
                if ("text".equals(block.getStr("type", ""))) {
                    text.append(block.getStr("text", ""));
                } else if ("tool_use".equals(block.getStr("type", ""))) {
                    Object input = block.get("input");
                    toolCalls.add(new AgentLlmToolCall(
                            block.getStr("id", ""),
                            block.getStr("name", ""),
                            input == null ? "{}" : AgentJsonCodec.toJson(input)));
                } else if (isThinkingBlock(block.getStr("type", ""))) {
                    providerItems.add(new AgentLlmProviderItem(
                            AgentLlmProtocol.ANTHROPIC_MESSAGES.name(), block.toString()));
                }
            }
        }

        JSONObject usage = root.getJSONObject("usage");
        int uncachedInput = intValue(usage, "input_tokens");
        int cacheRead = intValue(usage, "cache_read_input_tokens");
        int cacheWrite = intValue(usage, "cache_creation_input_tokens");
        int output = intValue(usage, "output_tokens");
        int totalInput = uncachedInput + cacheRead + cacheWrite;
        LlmTokenUsage tokenUsage = new LlmTokenUsage(
                totalInput, output, totalInput + output, cacheRead, cacheWrite);
        return new AgentLlmTurnResponse(toolCalls.isEmpty() ? "stop" : "tool_calls",
                text.toString(), toolCalls, responseBody, tokenUsage, "", "", providerItems);
    }

    private List<Map<String, Object>> toSystemBlocks(List<AgentLlmMessage> messages) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        boolean cacheBreakpointAdded = false;
        for (AgentLlmMessage message : messages) {
            if (message.role() != com.penmate.backend.domain.agent.model.AgentLlmMessageRole.SYSTEM) continue;
            LinkedHashMap<String, Object> block = new LinkedHashMap<>();
            block.put("type", "text");
            block.put("text", message.content());
            if (!cacheBreakpointAdded) {
                block.put("cache_control", Map.of("type", "ephemeral"));
                cacheBreakpointAdded = true;
            }
            blocks.add(block);
        }
        return blocks;
    }

    private List<Map<String, Object>> toMessages(List<AgentLlmMessage> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> pendingToolCalls = new HashSet<>();
        for (AgentLlmMessage message : messages) {
            if (message.role() == com.penmate.backend.domain.agent.model.AgentLlmMessageRole.SYSTEM) continue;
            if (message.role() == com.penmate.backend.domain.agent.model.AgentLlmMessageRole.ASSISTANT) {
                message.toolCalls().forEach(call -> pendingToolCalls.add(call.id()));
            } else if (message.role() == com.penmate.backend.domain.agent.model.AgentLlmMessageRole.TOOL
                    && !pendingToolCalls.remove(message.toolCallId())) {
                throw BusinessException.of(
                        "Claude tool result message is missing matching assistant tool call");
            }
            String role = message.role() == com.penmate.backend.domain.agent.model.AgentLlmMessageRole.ASSISTANT
                    ? "assistant" : "user";
            List<Map<String, Object>> blocks = toContentBlocks(message);
            if (blocks.isEmpty()) continue;
            appendOrMerge(result, role, blocks);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void addMessageCacheBreakpoint(List<Map<String, Object>> messages) {
        if (messages.isEmpty()) return;
        Object content = messages.get(0).get("content");
        if (!(content instanceof List<?> blocks) || blocks.isEmpty()) return;
        Object rawBlock = blocks.get(0);
        if (rawBlock instanceof Map<?, ?> block) {
            LinkedHashMap<String, Object> cachedBlock = new LinkedHashMap<>((Map<String, Object>) block);
            cachedBlock.put("cache_control", Map.of("type", "ephemeral"));
            ((List<Object>) blocks).set(0, cachedBlock);
        }
    }

    private List<Map<String, Object>> toContentBlocks(AgentLlmMessage message) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        switch (message.role()) {
            case USER -> blocks.add(Map.of("type", "text", "text", message.content()));
            case ASSISTANT -> {
                for (AgentLlmProviderItem item : message.providerItems()) {
                    if (!AgentLlmProtocol.ANTHROPIC_MESSAGES.name().equalsIgnoreCase(item.protocolCode())) continue;
                    JSONObject block = AgentJsonCodec.parseObj(item.payloadJson());
                    if (isThinkingBlock(block.getStr("type", ""))) {
                        blocks.add(new LinkedHashMap<>(block));
                    }
                }
                if (!message.content().isBlank()) {
                    blocks.add(Map.of("type", "text", "text", message.content()));
                }
                for (AgentLlmToolCallPayload call : message.toolCalls()) {
                    blocks.add(Map.of(
                            "type", "tool_use",
                            "id", call.id(),
                            "name", call.functionName(),
                            "input", AgentJsonCodec.parseObj(call.argumentsJson())));
                }
            }
            case TOOL -> blocks.add(Map.of(
                    "type", "tool_result",
                    "tool_use_id", message.toolCallId(),
                    "content", message.content()));
            case SYSTEM -> {
                // System messages are represented by the top-level system field.
            }
        }
        return blocks;
    }

    @SuppressWarnings("unchecked")
    private void appendOrMerge(List<Map<String, Object>> messages, String role,
                               List<Map<String, Object>> blocks) {
        if (!messages.isEmpty()) {
            Map<String, Object> previous = messages.get(messages.size() - 1);
            if (role.equals(previous.get("role"))) {
                ((List<Map<String, Object>>) previous.get("content")).addAll(blocks);
                return;
            }
        }
        LinkedHashMap<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", new ArrayList<>(blocks));
        messages.add(message);
    }

    private Map<String, Object> toTool(AgentLlmToolSchema schema) {
        LinkedHashMap<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", schema.toolCode());
        tool.put("description", schema.description());
        tool.put("input_schema", AgentJsonCodec.parseObj(schema.parametersJsonSchema()));
        return tool;
    }

    private void applyReasoningControls(Map<String, Object> body, AgentLlmExecutionConfig config) {
        var policy = config.reasoningPolicy();
        if (policy == null) return;
        if (policy.explicitSummary() && !"none".equalsIgnoreCase(policy.summary())) {
            throw BusinessException.of("Anthropic reasoning summaries are not configurable");
        }
        if (policy.explicitMode() && !"adaptive".equalsIgnoreCase(policy.mode())) {
            throw BusinessException.of("Anthropic supports only adaptive reasoning mode");
        }
        if (policy.explicitEffort() && !"none".equalsIgnoreCase(policy.effort())) {
            if (!Set.of("low", "medium", "high", "xhigh", "max").contains(policy.effort())) {
                throw BusinessException.of("Anthropic does not support reasoning effort " + policy.effort());
            }
            body.put("output_config", Map.of("effort", policy.effort()));
        }
        if ("adaptive".equalsIgnoreCase(policy.mode()) && !policy.disabled()) {
            body.put("thinking", Map.of("type", "adaptive"));
        }
    }

    private boolean isThinkingBlock(String type) {
        return "thinking".equalsIgnoreCase(type) || "redacted_thinking".equalsIgnoreCase(type);
    }

    private ResolvedRequest resolve(AgentLlmTurnRequest request, AgentLlmExecutionConfig config) {
        if (config == null) throw BusinessException.of("LLM execution config is required");
        if (request == null || blank(config.apiKey())
                || blank(config.modelName()) || blank(config.baseUrl())) {
            throw BusinessException.of("Claude Messages execution config is incomplete");
        }
        return new ResolvedRequest(messagesEndpoint(config.baseUrl()));
    }

    private String messagesEndpoint(String rawBaseUrl) {
        String baseUrl = rawBaseUrl.trim();
        while (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        if (baseUrl.endsWith("/messages")) return baseUrl;
        if (!baseUrl.endsWith("/v1")) baseUrl += "/v1";
        return baseUrl + "/messages";
    }

    private int intValue(JSONObject object, String key) {
        if (object == null) return 0;
        Integer value = object.getInt(key);
        return value == null ? 0 : Math.max(0, value);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record ResolvedRequest(String endpoint) {
    }
}
