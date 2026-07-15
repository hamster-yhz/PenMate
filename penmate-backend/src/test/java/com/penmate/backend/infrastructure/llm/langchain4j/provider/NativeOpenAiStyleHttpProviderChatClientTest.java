package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;
import com.penmate.backend.application.common.exception.BusinessException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NativeOpenAiStyleHttpProviderChatClientTest {

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_BUILDS_TEXT_REQUEST_BODY_WITH_SINGLE_USER_MESSAGE() {
        TestNativeClient client = new TestNativeClient();

        String requestBody = client.buildRequestBody("hello", "gpt-test");
        JSONObject root = AgentJsonCodec.parseObj(requestBody);
        JSONArray messages = root.getJSONArray("messages");

        assertThat(root.getStr("model")).isEqualTo("gpt-test");
        assertThat(messages).hasSize(1);
        assertThat(messages.getJSONObject(0).getStr("role")).isEqualTo("user");
        assertThat(messages.getJSONObject(0).getStr("content")).isEqualTo("hello");
    }

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_EXTRACTS_TEXT_CONTENT_FROM_OPENAI_STYLE_RESPONSE() {
        TestNativeClient client = new TestNativeClient();

        String content = client.extractContent("""
                {
                  "choices": [{
                    "message": {
                      "content": "final answer"
                    }
                  }]
                }
                """);

        assertThat(content).isEqualTo("final answer");
    }

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_SHOULD_EXTRACT_TOKEN_USAGE_FROM_USAGE_BLOCK() {
        TestNativeClient client = new TestNativeClient();
        String responseBody = """
                {
                  "choices": [{
                    "finish_reason": "stop",
                    "message": {
                      "content": "final answer"
                    }
                  }],
                  "usage": {
                    "prompt_tokens": 11,
                    "completion_tokens": 7,
                    "total_tokens": 18
                  }
                }
                """;

        AgentLlmTurnResponse response = client.extractTurnResponse(responseBody);

        assertThat(response.tokenUsage()).isEqualTo(new LlmTokenUsage(11, 7, 18));
    }

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_SHOULD_DEFAULT_TOKEN_USAGE_TO_ZERO_WHEN_USAGE_BLOCK_MISSING() {
        TestNativeClient client = new TestNativeClient();
        String responseBody = """
                {
                  "choices": [{
                    "finish_reason": "stop",
                    "message": {
                      "content": "final answer"
                    }
                  }]
                }
                """;

        AgentLlmTurnResponse response = client.extractTurnResponse(responseBody);

        assertThat(response.tokenUsage()).isEqualTo(LlmTokenUsage.ZERO);
    }

    @Test
    void should_extract_cached_prompt_token_details_when_the_provider_reports_them() {
        TestNativeClient client = new TestNativeClient();
        AgentLlmTurnResponse response = client.extractTurnResponse("""
                {
                  "choices": [{"finish_reason":"stop","message":{"content":"ok"}}],
                  "usage": {
                    "prompt_tokens": 120,
                    "completion_tokens": 8,
                    "total_tokens": 128,
                    "prompt_tokens_details": {"cached_tokens": 96},
                    "cache_creation_input_tokens": 12
                  }
                }
                """);

        assertThat(response.tokenUsage()).isEqualTo(new LlmTokenUsage(120, 8, 128, 96, 12));
    }

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_SHOULD_DEFAULT_MISSING_USAGE_FIELDS_TO_ZERO() {
        TestNativeClient client = new TestNativeClient();
        String responseBody = """
                {
                  "choices": [{
                    "finish_reason": "stop",
                    "message": {
                      "content": "final answer"
                    }
                  }],
                  "usage": {
                    "prompt_tokens": 11
                  }
                }
                """;

        AgentLlmTurnResponse response = client.extractTurnResponse(responseBody);

        assertThat(response.tokenUsage()).isEqualTo(new LlmTokenUsage(11, 0, 0));
    }

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_DOES_NOT_KEEP_DIRECT_OBJECT_MAPPER_DEPENDENCY() {
        assertThat(Arrays.stream(NativeOpenAiStyleHttpProviderChatClient.class.getDeclaredFields())
                .map(Field::getType)
                .toList())
                .noneMatch(type -> type.equals(com.fasterxml.jackson.databind.ObjectMapper.class));
    }

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_DOES_NOT_INTERRUPT_THREAD_ON_IO_EXCEPTION() throws Exception {
        TestNativeClient client = new TestNativeClient();
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("boom"));
        injectHttpClient(client, httpClient);
        Thread.interrupted();

        assertThatThrownBy(() -> client.generate("hello", executionConfig()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("LLM request failed: boom");
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_RESTORES_INTERRUPT_STATUS_ON_INTERRUPTED_EXCEPTION() throws Exception {
        TestNativeClient client = new TestNativeClient();
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("stopped"));
        injectHttpClient(client, httpClient);
        Thread.interrupted();

        assertThatThrownBy(() -> client.generate("hello", executionConfig()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("LLM request failed: stopped");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();
    }

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_LOGS_RAW_RESPONSE_WHEN_TURN_RESPONSE_JSON_IS_INVALID() {
        TestNativeClient client = new TestNativeClient();

        try (LogCapture logCapture = captureLogs()) {
            assertThatThrownBy(() -> client.extractTurnResponse("not-json-response"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Failed to parse LLM response");

            assertThat(logCapture.joinedMessages())
                    .contains("llm.turn.response.parse.failed")
                    .contains("rawResponseSnippet=not-json-response");
        }
    }

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_LOGS_RESPONSE_STRUCTURE_WHEN_TURN_RESPONSE_MESSAGE_SHAPE_IS_INVALID() {
        TestNativeClient client = new TestNativeClient();
        String responseBody = """
                {
                  "choices": [{
                    "message": "unexpected-string"
                  }]
                }
                """;

        try (LogCapture logCapture = captureLogs()) {
            assertThatThrownBy(() -> client.extractTurnResponse(responseBody))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Failed to parse LLM response");

            assertThat(logCapture.joinedMessages())
                    .contains("llm.turn.response.parse.failed")
                    .contains("choicesPresent=true")
                    .contains("firstChoicePresent=true")
                    .contains("messagePresent=true")
                    .contains("messageType=string")
                    .contains("rawResponseSnippet=");
        }
    }

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_DOES_NOT_LOG_CONTENT_MISSING_WHEN_TOOL_CALLS_RESPONSE_OMITS_CONTENT() {
        TestNativeClient client = new TestNativeClient();
        String responseBody = """
                {
                  "choices": [{
                    "finish_reason": "tool_calls",
                    "message": {
                      "tool_calls": [{
                        "id": "call_1",
                        "type": "function",
                        "function": {
                          "name": "context_enhancer",
                          "arguments": "{\\\"prompt\\\":\\\"hello\\\"}"
                        }
                      }]
                    }
                  }]
                }
                """;

        try (LogCapture logCapture = captureLogs()) {
            AgentLlmTurnResponse response = client.extractTurnResponse(responseBody);

            assertThat(response.finishReason()).isEqualTo("tool_calls");
            assertThat(response.assistantText()).isEmpty();
            assertThat(response.toolCalls()).hasSize(1);
            assertThat(logCapture.joinedMessages())
                    .doesNotContain("llm.turn.response.content.missing")
                    .doesNotContain("llm.turn.response.tool_calls.expected_but_missing");
        }
    }

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_LOGS_TURN_REQUEST_AND_RESPONSE_SNIPPETS_ON_SUCCESS() throws Exception {
        TestNativeClient client = new TestNativeClient();
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "choices": [{
                    "finish_reason": "stop",
                    "message": {
                      "content": "ok"
                    }
                  }]
                }
                """);
        injectHttpClient(client, httpClient);

        try (LogCapture logCapture = captureLogs()) {
            AgentLlmTurnResponse actual = client.generateTurn(
                    new AgentLlmTurnRequest(
                            List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("hello turn")),
                            List.of(),
                            null
                    ),
                    executionConfig()
            );

            assertThat(actual.assistantText()).isEqualTo("ok");
            assertThat(logCapture.joinedMessages())
                    .contains("llm.turn.request.payload")
                    .contains("requestBodySnippet=")
                    .contains("\"content\":\"hello turn\"")
                    .contains("llm.turn.response.raw")
                    .contains("responseBodySnippet=")
                    .contains("\"content\": \"ok\"");
        }
    }

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_LOGS_PARSE_CONTEXT_WHEN_GENERATE_TURN_RECEIVES_INVALID_RESPONSE() throws Exception {
        TestNativeClient client = new TestNativeClient();
        HttpClient httpClient = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("not-json-response");
        injectHttpClient(client, httpClient);

        try (LogCapture logCapture = captureLogs()) {
            assertThatThrownBy(() -> client.generateTurn(
                    new AgentLlmTurnRequest(
                            List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("hello turn")),
                            List.of(),
                            null
                    ),
                    executionConfig()
            ))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Failed to parse LLM response");

            assertThat(logCapture.joinedMessages())
                    .contains("llm.turn.request.payload")
                    .contains("requestBodySnippet=")
                    .contains("\"content\":\"hello turn\"")
                    .contains("llm.turn.response.raw")
                    .contains("responseBodySnippet=not-json-response")
                    .contains("llm.turn.response.parse.context")
                    .contains("llm.turn.response.parse.failed");
        }
    }

    private AgentLlmExecutionConfig executionConfig() {
        return new AgentLlmExecutionConfig(1L, "test-provider", "https://example.com/v1", "sk-test", "gpt-test", "USER_KEY", 6);
    }

    private void injectHttpClient(TestNativeClient client, HttpClient httpClient) throws Exception {
        Field field = NativeOpenAiStyleHttpProviderChatClient.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(client, httpClient);
    }

    private LogCapture captureLogs() {
        Logger logger = (Logger) LogManager.getLogger(NativeOpenAiStyleHttpProviderChatClient.class);
        TestLogAppender appender = new TestLogAppender("native-openai-style-test-appender");
        appender.start();
        Level originalLevel = logger.getLevel();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        return new LogCapture(logger, appender, originalLevel);
    }

    private static final class TestNativeClient extends NativeOpenAiStyleHttpProviderChatClient {

        @Override
        public boolean supports(String providerCode) {
            return true;
        }
    }

    private static final class LogCapture implements AutoCloseable {

        private final Logger logger;
        private final TestLogAppender appender;
        private final Level originalLevel;

        private LogCapture(Logger logger, TestLogAppender appender, Level originalLevel) {
            this.logger = logger;
            this.appender = appender;
            this.originalLevel = originalLevel;
        }

        private String joinedMessages() {
            return String.join("\n", appender.messages());
        }

        @Override
        public void close() {
            logger.removeAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }
    }

    private static final class TestLogAppender extends AbstractAppender {

        private final List<String> messages = new ArrayList<>();

        private TestLogAppender(String name) {
            super(name, null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            messages.add(event.toImmutable().getMessage().getFormattedMessage());
        }

        private List<String> messages() {
            return messages;
        }
    }
}
