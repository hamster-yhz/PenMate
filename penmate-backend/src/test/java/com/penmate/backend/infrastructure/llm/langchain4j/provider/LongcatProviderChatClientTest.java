package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LongcatProviderChatClientTest {

    @Test
    void UT_INFRA_LLM_LONGCAT_PROVIDER_CHAT_CLIENT_SHOULD_KEEP_CALLER_MODEL_NAME_IN_TURN_REQUEST_BODY() throws Exception {
        LongcatProviderChatClient client = new LongcatProviderChatClient();
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        String[] capturedRequestBody = new String[1];

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    capturedRequestBody[0] = readBody(request);
                    return response;
                });
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

        AgentLlmTurnResponse actual = client.generateTurn(
                new AgentLlmTurnRequest(
                        List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("hello")),
                        List.of(),
                        null
                ),
                new AgentLlmExecutionConfig(
                        2053134348617666560L,
                        "longcat",
                        "https://api.longcat.chat/openai/v1",
                        "sk-test",
                        "LongCat-Flash-Thinking",
                        "MODEL_CONFIG",
                        6
                )
        );

        assertThat(actual.assistantText()).isEqualTo("ok");
        JSONObject root = AgentJsonCodec.parseObj(capturedRequestBody[0]);
        assertThat(root.getStr("model")).isEqualTo("LongCat-Flash-Thinking");
    }

    @Test
    void UT_INFRA_LLM_LONGCAT_PROVIDER_CHAT_CLIENT_SHOULD_SEND_PROVIDER_COMPATIBLE_TOOL_SCHEMAS_WITHOUT_TOP_LEVEL_COMBINATORS() throws Exception {
        LongcatProviderChatClient client = new LongcatProviderChatClient();
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        String[] capturedRequestBody = new String[1];

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    capturedRequestBody[0] = readBody(request);
                    return response;
                });
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

        AgentLlmTurnResponse actual = client.generateTurn(
                new AgentLlmTurnRequest(
                        List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("继续写第 3 章")),
                        new com.penmate.backend.application.agent.tool.definition.InMemoryAgentToolDefinitionSource(List.of(
                                new com.penmate.backend.application.agent.tool.definition.BookCrudToolDefinition(),
                                new com.penmate.backend.application.agent.tool.definition.TodoPlannerToolDefinition(),
                                new com.penmate.backend.application.agent.tool.definition.ChapterEditToolDefinition()
                        )).listLlmSchemas(),
                        "auto"
                ),
                new AgentLlmExecutionConfig(
                        2053134348617666560L,
                        "longcat",
                        "https://api.longcat.chat/openai/v1",
                        "sk-test",
                        "LongCat-Flash-Thinking",
                        "MODEL_CONFIG",
                        6
                )
        );

        assertThat(actual.assistantText()).isEqualTo("ok");
        JSONObject root = AgentJsonCodec.parseObj(capturedRequestBody[0]);
        assertThat(root.getJSONArray("tools")).hasSize(2);
        for (Object toolObject : root.getJSONArray("tools")) {
            JSONObject parameters = ((JSONObject) toolObject)
                    .getJSONObject("function")
                    .getJSONObject("parameters");
            assertThat(parameters.getStr("type")).isEqualTo("object");
            assertThat(parameters.containsKey("oneOf")).isFalse();
            assertThat(parameters.containsKey("anyOf")).isFalse();
            assertThat(parameters.containsKey("allOf")).isFalse();
            assertThat(parameters.containsKey("enum")).isFalse();
            assertThat(parameters.containsKey("not")).isFalse();
        }
    }

    @Test
    void UT_INFRA_LLM_OPENAI_PROVIDER_CHAT_CLIENT_SHOULD_PRESERVE_REQUIRED_TOOL_CHOICE_FOR_LONGCAT_ENDPOINT() throws Exception {
        OpenAiProviderChatClient client = new OpenAiProviderChatClient();
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        String[] capturedRequestBody = new String[1];

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    capturedRequestBody[0] = readBody(request);
                    return response;
                });
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

        AgentLlmTurnResponse actual = client.generateTurn(
                new AgentLlmTurnRequest(
                        List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("请分析请求")),
                        List.of(new AgentLlmToolSchema(
                                "structured_output",
                                "Return structured output.",
                                """
                                        {
                                          "type": "object",
                                          "properties": {
                                            "behaviorType": {
                                              "type": "string"
                                            }
                                          },
                                          "required": ["behaviorType"],
                                          "additionalProperties": false
                                        }
                                        """
                        )),
                        "required"
                ),
                new AgentLlmExecutionConfig(
                        2053134348617666560L,
                        "openai",
                        "https://api.longcat.chat/openai/v1",
                        "sk-test",
                        "LongCat-Flash-Thinking",
                        "MODEL_CONFIG",
                        6
                )
        );

        assertThat(actual.assistantText()).isEqualTo("ok");
        JSONObject root = AgentJsonCodec.parseObj(capturedRequestBody[0]);
        assertThat(root.get("response_format")).isNull();
        assertThat(root.getJSONArray("tools")).hasSize(1);
        assertThat(root.getStr("tool_choice")).isEqualTo("required");
    }

    private void injectHttpClient(NativeOpenAiStyleHttpProviderChatClient client, HttpClient httpClient) throws Exception {
        Field field = NativeOpenAiStyleHttpProviderChatClient.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(client, httpClient);
    }

    private String readBody(HttpRequest request) {
        try {
            HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
            BodyCollector collector = new BodyCollector();
            publisher.subscribe(collector);
            return collector.awaitBody();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read request body", ex);
        }
    }

    private static final class BodyCollector implements Flow.Subscriber<ByteBuffer> {

        private final List<ByteBuffer> chunks = new ArrayList<>();
        private final CompletableFuture<Void> done = new CompletableFuture<>();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            chunks.add(item.slice());
        }

        @Override
        public void onError(Throwable throwable) {
            done.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            done.complete(null);
        }

        private String awaitBody() throws Exception {
            done.get();
            int total = chunks.stream().mapToInt(ByteBuffer::remaining).sum();
            byte[] bytes = new byte[total];
            int offset = 0;
            for (ByteBuffer chunk : chunks) {
                int remaining = chunk.remaining();
                chunk.get(bytes, offset, remaining);
                offset += remaining;
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
