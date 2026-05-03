package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.json.AgentJsons;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NativeOpenAiStyleHttpProviderChatClientToolModeTest {

    private final TestNativeClient client = new TestNativeClient();

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_EXTRACTS_TURN_RESPONSE_WITH_TOOL_CALLS() {
        String responseBody = """
                {
                  "choices": [{
                    "finish_reason": "tool_calls",
                    "message": {
                      "content": "",
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

        AgentLlmTurnResponse response = client.extractTurnResponse(responseBody);

        assertThat(response.finishReason()).isEqualTo("tool_calls");
        assertThat(response.assistantText()).isEmpty();
        assertThat(response.requestsToolCalls()).isTrue();
        assertThat(response.rawResponseJson()).isEqualTo(responseBody);
        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.toolCalls().get(0).id()).isEqualTo("call_1");
        assertThat(response.toolCalls().get(0).toolCode()).isEqualTo("context_enhancer");
        assertThat(response.toolCalls().get(0).argumentsJson()).isEqualTo("{\"prompt\":\"hello\"}");
    }

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_BUILDS_TURN_REQUEST_BODY_WITH_TOOLS_AND_AUTO_TOOL_CHOICE() {
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(Map.of("role", "user", "content", "hello")),
                List.of(new AgentLlmToolSchema(
                        "context_enhancer",
                        "补充上下文",
                        """
                                {
                                  "type": "object",
                                  "properties": {
                                    "prompt": {
                                      "type": "string"
                                    }
                                  },
                                  "required": ["prompt"]
                                }
                                """
                )),
                "auto"
        );

        String requestBody = client.buildTurnRequestBody(request, "gpt-test");
        JSONObject root = AgentJsons.parseObj(requestBody);
        JSONArray messages = root.getJSONArray("messages");
        JSONArray tools = root.getJSONArray("tools");
        JSONObject firstTool = tools.getJSONObject(0);
        JSONObject function = firstTool.getJSONObject("function");

        assertThat(root.getStr("model")).isEqualTo("gpt-test");
        assertThat(root.getStr("tool_choice")).isEqualTo("auto");
        assertThat(messages.size()).isEqualTo(1);
        assertThat(messages.getJSONObject(0).getStr("role")).isEqualTo("user");
        assertThat(messages.getJSONObject(0).getStr("content")).isEqualTo("hello");
        assertThat(tools.size()).isEqualTo(1);
        assertThat(firstTool.getStr("type")).isEqualTo("function");
        assertThat(function.getStr("name")).isEqualTo("context_enhancer");
        assertThat(function.getStr("description")).isEqualTo("补充上下文");
        assertThat(function.getJSONObject("parameters").getStr("type")).isEqualTo("object");
        assertThat(function.getJSONObject("parameters").getJSONObject("properties").getJSONObject("prompt").getStr("type"))
                .isEqualTo("string");
    }

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_OMITS_TOOLS_AND_TOOL_CHOICE_WHEN_TOOL_LIST_IS_EMPTY() {
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(Map.of("role", "user", "content", "hello")),
                List.of(),
                "auto"
        );

        String requestBody = client.buildTurnRequestBody(request, "gpt-test");
        JSONObject root = AgentJsons.parseObj(requestBody);

        assertThat(root.getJSONArray("tools")).isNull();
        assertThat(root.getStr("tool_choice", null)).isNull();
    }

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_EXTRACTS_MULTIPLE_TOOL_CALLS_IN_ORDER() {
        String responseBody = """
                {
                  "choices": [{
                    "finish_reason": "tool_calls",
                    "message": {
                      "content": "need tools",
                      "tool_calls": [
                        {
                          "id": "call_1",
                          "type": "function",
                          "function": {
                            "name": "context_enhancer",
                            "arguments": "{\\\"prompt\\\":\\\"hello\\\"}"
                          }
                        },
                        {
                          "id": "call_2",
                          "type": "function",
                          "function": {
                            "name": "book_crud",
                            "arguments": "{\\\"operation\\\":\\\"list\\\"}"
                          }
                        }
                      ]
                    }
                  }]
                }
                """;

        AgentLlmTurnResponse response = client.extractTurnResponse(responseBody);

        assertThat(response.assistantText()).isEqualTo("need tools");
        assertThat(response.toolCalls()).extracting(call -> call.toolCode())
                .containsExactly("context_enhancer", "book_crud");
        assertThat(response.toolCalls()).extracting(call -> call.id())
                .containsExactly("call_1", "call_2");
    }

    private static final class TestNativeClient extends NativeOpenAiStyleHttpProviderChatClient {

        @Override
        public boolean supports(String providerCode) {
            return true;
        }
    }
}
