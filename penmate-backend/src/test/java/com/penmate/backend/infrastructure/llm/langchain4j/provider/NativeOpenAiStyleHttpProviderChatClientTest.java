package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;

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

    private AgentLlmExecutionConfig executionConfig() {
        return new AgentLlmExecutionConfig(1L, "test-provider", "https://example.com/v1", "sk-test", "gpt-test", "USER_KEY");
    }

    private void injectHttpClient(TestNativeClient client, HttpClient httpClient) throws Exception {
        Field field = NativeOpenAiStyleHttpProviderChatClient.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(client, httpClient);
    }

    private static final class TestNativeClient extends NativeOpenAiStyleHttpProviderChatClient {

        @Override
        public boolean supports(String providerCode) {
            return true;
        }
    }
}
