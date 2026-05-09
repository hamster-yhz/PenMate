package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmToolCall;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public AgentLlmTurnResponse generateTurn(AgentLlmTurnRequest request,
                                             AgentLlmExecutionConfig executionConfig) {
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

        AnthropicChatModel model = builder.build();
        List<ChatMessage> messages = toChatMessages(request == null ? List.of() : request.messages());
        List<ToolSpecification> toolSpecifications = toToolSpecifications(request == null ? List.of() : request.tools());
        Response<AiMessage> response = toolSpecifications.isEmpty()
                ? model.generate(messages)
                : model.generate(messages, toolSpecifications);
        return toTurnResponse(response);
    }

    @Override
    public boolean supports(String providerCode) {
        return "claude".equalsIgnoreCase(providerCode);
    }

    private List<ChatMessage> toChatMessages(List<Map<String, Object>> rawMessages) {
        List<ChatMessage> messages = new ArrayList<>();
        Map<String, String> toolNamesById = new LinkedHashMap<>();
        for (Map<String, Object> rawMessage : rawMessages) {
            if (rawMessage == null) {
                continue;
            }
            String role = stringValue(rawMessage.get("role"));
            if ("system".equalsIgnoreCase(role)) {
                messages.add(SystemMessage.from(stringValue(rawMessage.get("content"))));
                continue;
            }
            if ("user".equalsIgnoreCase(role)) {
                messages.add(UserMessage.from(stringValue(rawMessage.get("content"))));
                continue;
            }
            if ("assistant".equalsIgnoreCase(role)) {
                List<ToolExecutionRequest> requests = toToolExecutionRequests(rawMessage.get("tool_calls"));
                requests.forEach(request -> toolNamesById.put(request.id(), request.name()));
                messages.add(AiMessage.from(stringValue(rawMessage.get("content")), requests));
                continue;
            }
            if ("tool".equalsIgnoreCase(role)) {
                String toolCallId = stringValue(rawMessage.get("tool_call_id"));
                String toolName = toolNamesById.get(toolCallId);
                if (toolName == null || toolName.isBlank()) {
                    throw BusinessException.of("Claude tool result message is missing matching assistant tool call");
                }
                messages.add(ToolExecutionResultMessage.from(
                        toolCallId,
                        toolName,
                        stringValue(rawMessage.get("content"))
                ));
            }
        }
        return messages;
    }

    private List<ToolExecutionRequest> toToolExecutionRequests(Object toolCallsValue) {
        List<ToolExecutionRequest> requests = new ArrayList<>();
        for (Object item : toList(toolCallsValue)) {
            Map<String, Object> payload = mapValue(item);
            Map<String, Object> function = mapValue(payload.get("function"));
            requests.add(ToolExecutionRequest.builder()
                    .id(stringValue(payload.get("id")))
                    .name(stringValue(function.get("name")))
                    .arguments(stringValue(function.get("arguments")))
                    .build());
        }
        return requests;
    }

    private List<ToolSpecification> toToolSpecifications(List<AgentLlmToolSchema> schemas) {
        List<ToolSpecification> specifications = new ArrayList<>();
        for (AgentLlmToolSchema schema : schemas) {
            JSONObject parametersRoot = AgentJsonCodec.parseObj(schema.parametersJsonSchema());
            JSONObject propertiesObject = parametersRoot.getJSONObject("properties");
            Map<String, Map<String, Object>> properties = new LinkedHashMap<>();
            if (propertiesObject != null) {
                for (String key : propertiesObject.keySet()) {
                    properties.put(key, new LinkedHashMap<>(mapValue(propertiesObject.get(key))));
                }
            }
            List<String> required = new ArrayList<>();
            Object requiredValue = parametersRoot.get("required");
            for (Object item : toList(requiredValue)) {
                required.add(String.valueOf(item));
            }
            ToolParameters parameters = ToolParameters.builder()
                    .type(parametersRoot.getStr("type", "object"))
                    .properties(properties)
                    .required(required)
                    .build();
            specifications.add(ToolSpecification.builder()
                    .name(schema.toolCode())
                    .description(schema.description())
                    .parameters(parameters)
                    .build());
        }
        return specifications;
    }

    private AgentLlmTurnResponse toTurnResponse(Response<AiMessage> response) {
        AiMessage content = response == null ? null : response.content();
        List<AgentLlmToolCall> toolCalls = new ArrayList<>();
        if (content != null && content.toolExecutionRequests() != null) {
            for (ToolExecutionRequest request : content.toolExecutionRequests()) {
                toolCalls.add(new AgentLlmToolCall(request.id(), request.name(), request.arguments()));
            }
        }
        String finishReason = response != null && response.finishReason() == FinishReason.TOOL_EXECUTION
                ? "tool_calls"
                : "stop";
        return new AgentLlmTurnResponse(
                finishReason,
                content == null ? "" : content.text(),
                toolCalls,
                response == null ? "{}" : String.valueOf(response)
        );
    }

    private List<?> toList(Object value) {
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    private Map<String, Object> mapValue(Object value) {
        if (value instanceof JSONObject object) {
            return new LinkedHashMap<>(object);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return Map.of();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}

