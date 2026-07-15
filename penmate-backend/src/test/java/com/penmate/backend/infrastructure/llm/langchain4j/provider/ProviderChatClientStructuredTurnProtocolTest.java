package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

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
    void UT_INFRA_LLM_CLAUDE_PROVIDER_CHAT_CLIENT_FAILS_FAST_WHEN_TOOL_RESULT_MESSAGE_CANNOT_BE_BOUND_TO_TOOL_NAME() throws Exception {
        ClaudeProviderChatClient client = new ClaudeProviderChatClient();
        Method method = ClaudeProviderChatClient.class.getDeclaredMethod("toChatMessages", List.class);
        method.setAccessible(true);

        assertThatThrownBy(() -> method.invoke(client, List.of(
                AgentLlmMessage.tool("call_missing", "done")
        )))
                .hasRootCauseInstanceOf(BusinessException.class)
                .rootCause()
                .hasMessage("Claude tool result message is missing matching assistant tool call");
    }

    @Test
    void UT_INFRA_LLM_CLAUDE_PROVIDER_CHAT_CLIENT_MAPS_ASSISTANT_TOOL_CALL_AND_TOOL_RESULT_MESSAGES_TO_ANTHROPIC_NATIVE_MESSAGE_TYPES() throws Exception {
        ClaudeProviderChatClient client = new ClaudeProviderChatClient();
        Method method = ClaudeProviderChatClient.class.getDeclaredMethod("toChatMessages", List.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<ChatMessage> messages = (List<ChatMessage>) method.invoke(client, List.of(
                AgentLlmMessage.assistant(
                        "need tool",
                        List.of(new AgentLlmToolCallPayload(
                                "call_1",
                                "function",
                                "context_enhancer",
                                "{\"prompt\":\"hello\"}"
                        ))
                ),
                AgentLlmMessage.tool("call_1", "tool output")
        ));

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).type()).isEqualTo(ChatMessageType.AI);
        assertThat(((AiMessage) messages.get(0)).toolExecutionRequests())
                .extracting(request -> request.id(), request -> request.name(), request -> request.arguments())
                .containsExactly(tuple("call_1", "context_enhancer", "{\"prompt\":\"hello\"}"));
        assertThat(messages.get(1).type()).isEqualTo(ChatMessageType.TOOL_EXECUTION_RESULT);
        ToolExecutionResultMessage toolResultMessage = (ToolExecutionResultMessage) messages.get(1);
        assertThat(toolResultMessage.id()).isEqualTo("call_1");
        assertThat(toolResultMessage.toolName()).isEqualTo("context_enhancer");
        assertThat(toolResultMessage.text()).isEqualTo("tool output");
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
