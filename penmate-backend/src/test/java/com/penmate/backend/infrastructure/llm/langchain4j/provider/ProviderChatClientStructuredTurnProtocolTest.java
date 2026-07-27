package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderChatClientStructuredTurnProtocolTest {

    @Test
    void UT_INFRA_LLM_CLAUDE_PROVIDER_CHAT_CLIENT_USES_NATIVE_ANTHROPIC_TURN_PROTOCOL_INSTEAD_OF_OPENAI_STYLE_HTTP_BASE_CLASS() {
        ClaudeProviderChatClient client = new ClaudeProviderChatClient();

        assertThat(client).isNotInstanceOf(NativeOpenAiStyleHttpProviderChatClient.class);
    }

    @Test
    void UT_INFRA_LLM_OPENAI_PROVIDER_CHAT_CLIENT_RESOLVES_STRUCTURED_TURN_ENDPOINT_TO_V1_CHAT_COMPLETIONS() {
        TestOpenAiProviderChatClient client = new TestOpenAiProviderChatClient();

        assertThat(client.resolveTurnEndpoint("https://api.openai.com"))
                .isEqualTo("https://api.openai.com/v1/chat/completions");
        assertThat(client.resolveTurnEndpoint("https://api.openai.com/"))
                .isEqualTo("https://api.openai.com/v1/chat/completions");
        assertThat(client.resolveTurnEndpoint("https://api.openai.com/v1"))
                .isEqualTo("https://api.openai.com/v1/chat/completions");
        assertThat(client.resolveTurnEndpoint("https://api.openai.com/v1/chat/completions"))
                .isEqualTo("https://api.openai.com/v1/chat/completions");
    }

    @Test
    void UT_INFRA_LLM_GEMINI_PROVIDER_CHAT_CLIENT_RESOLVES_STRUCTURED_TURN_ENDPOINT_TO_OPENAI_COMPATIBLE_CHAT_COMPLETIONS() {
        TestGeminiProviderChatClient client = new TestGeminiProviderChatClient();

        assertThat(client.resolveTurnEndpoint("https://generativelanguage.googleapis.com/v1beta"))
                .isEqualTo("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions");
        assertThat(client.resolveTurnEndpoint("https://generativelanguage.googleapis.com/v1beta/"))
                .isEqualTo("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions");
        assertThat(client.resolveTurnEndpoint("https://generativelanguage.googleapis.com/v1beta/openai"))
                .isEqualTo("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions");
        assertThat(client.resolveTurnEndpoint("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"))
                .isEqualTo("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions");
    }

    @Test
    void UT_INFRA_LLM_OPENAI_COMPATIBLE_PROVIDER_CHAT_CLIENT_RESOLVES_STRUCTURED_TURN_ENDPOINT_TO_CHAT_COMPLETIONS_WITHOUT_FORCING_V1_SUFFIX() {
        TestOpenAiCompatibleProviderChatClient client = new TestOpenAiCompatibleProviderChatClient();

        assertThat(client.resolveTurnEndpoint("https://example.com/proxy/openai"))
                .isEqualTo("https://example.com/proxy/openai/chat/completions");
        assertThat(client.resolveTurnEndpoint("https://example.com/proxy/openai/"))
                .isEqualTo("https://example.com/proxy/openai/chat/completions");
        assertThat(client.resolveTurnEndpoint("https://example.com/proxy/openai/chat/completions"))
                .isEqualTo("https://example.com/proxy/openai/chat/completions");
    }

    @Test
    void UT_INFRA_LLM_OPENAI_COMPATIBLE_PROVIDER_CHAT_CLIENT_NORMALIZES_COMPLETE_CHAT_COMPLETIONS_ENDPOINT_WITH_TRAILING_SLASH_AND_BLANK_INPUT() {
        TestOpenAiCompatibleProviderChatClient client = new TestOpenAiCompatibleProviderChatClient();

        assertThat(client.resolveTurnEndpoint("https://example.com/proxy/openai/chat/completions/"))
                .isEqualTo("https://example.com/proxy/openai/chat/completions");
        assertThat(client.resolveTurnEndpoint("   "))
                .isNull();
    }

    @Test
    void UT_INFRA_LLM_CLAUDE_PROVIDER_CHAT_CLIENT_FAILS_FAST_WHEN_TOOL_RESULT_MESSAGE_CANNOT_BE_BOUND_TO_TOOL_NAME() {
        ClaudeProviderChatClient client = new ClaudeProviderChatClient();

        assertThatThrownBy(() -> client.buildRequestBody(new AgentLlmTurnRequest(List.of(
                AgentLlmMessage.tool("call_missing", "done")), List.of(), "none"), claudeConfig()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Claude tool result message is missing matching assistant tool call");
    }

    @Test
    void UT_INFRA_LLM_CLAUDE_PROVIDER_CHAT_CLIENT_MAPS_ASSISTANT_TOOL_CALL_AND_TOOL_RESULT_MESSAGES_TO_ANTHROPIC_NATIVE_MESSAGE_TYPES() {
        ClaudeProviderChatClient client = new ClaudeProviderChatClient();
        String body = client.buildRequestBody(new AgentLlmTurnRequest(List.of(
                AgentLlmMessage.assistant(
                        "need tool",
                        List.of(new AgentLlmToolCallPayload(
                                "call_1",
                                "function",
                                "custom_tool",
                                "{\"prompt\":\"hello\"}"
                        ))
                ),
                AgentLlmMessage.tool("call_1", "tool output")
        ), List.of(), "none"), claudeConfig());

        JSONArray messages = AgentJsonCodec.parseObj(body).getJSONArray("messages");
        JSONObject toolUse = messages.getJSONObject(0).getJSONArray("content").getJSONObject(1);
        JSONObject toolResult = messages.getJSONObject(1).getJSONArray("content").getJSONObject(0);
        assertThat(toolUse.getStr("type")).isEqualTo("tool_use");
        assertThat(toolUse.getStr("id")).isEqualTo("call_1");
        assertThat(toolUse.getStr("name")).isEqualTo("custom_tool");
        assertThat(toolUse.getJSONObject("input").getStr("prompt")).isEqualTo("hello");
        assertThat(toolResult.getStr("type")).isEqualTo("tool_result");
        assertThat(toolResult.getStr("tool_use_id")).isEqualTo("call_1");
        assertThat(toolResult.getStr("content")).isEqualTo("tool output");
    }

    private AgentLlmExecutionConfig claudeConfig() {
        return AgentLlmExecutionConfig.builder()
                .baseUrl("https://api.anthropic.com")
                .apiKey("test")
                .modelName("claude-sonnet-4-6")
                .maxOutputTokens(1024)
                .build();
    }

    private static final class TestOpenAiProviderChatClient extends OpenAiProviderChatClient {

        private String resolveTurnEndpoint(String rawBaseUrl) {
            return resolveChatCompletionsEndpoint(rawBaseUrl);
        }

    }

    private static final class TestGeminiProviderChatClient extends GeminiProviderChatClient {

        private String resolveTurnEndpoint(String rawBaseUrl) {
            return resolveChatCompletionsEndpoint(rawBaseUrl);
        }
    }

    private static final class TestOpenAiCompatibleProviderChatClient extends OpenAiCompatibleProviderChatClient {

        private String resolveTurnEndpoint(String rawBaseUrl) {
            return resolveChatCompletionsEndpoint(rawBaseUrl);
        }
    }
}
