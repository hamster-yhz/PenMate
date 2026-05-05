package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.BookCrudToolDefinition;
import com.penmate.backend.application.agent.tool.definition.ContextEnhancerToolDefinition;
import com.penmate.backend.application.agent.tool.definition.InMemoryAgentToolDefinitionSource;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
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
        JSONObject root = AgentJsonCodec.parseObj(requestBody);
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
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_BUILDS_TURN_REQUEST_BODY_WITH_MULTIPLE_TOOLS_IN_ORDER() {
        AgentToolDefinitionSource definitionSource = new InMemoryAgentToolDefinitionSource(List.of(
                new ContextEnhancerToolDefinition(),
                new BookCrudToolDefinition()
        ));
        AgentLlmToolSchema contextEnhancer = new AgentLlmToolSchema(
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
        );
        AgentLlmToolSchema bookCrud = definitionSource.listLlmSchemas().stream()
                .filter(schema -> "book_crud".equals(schema.toolCode()))
                .findFirst()
                .orElseThrow();
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(Map.of("role", "user", "content", "hello")),
                List.of(contextEnhancer, bookCrud),
                "auto"
        );

        String requestBody = client.buildTurnRequestBody(request, "gpt-test");
        JSONObject root = AgentJsonCodec.parseObj(requestBody);
        JSONArray tools = root.getJSONArray("tools");

        assertThat(tools).hasSize(2);
        assertThat(tools.getJSONObject(0).getJSONObject("function").getStr("name")).isEqualTo("context_enhancer");
        assertThat(tools.getJSONObject(1).getJSONObject("function").getStr("name")).isEqualTo("book_crud");
        assertThat(tools.getJSONObject(1).getJSONObject("function").getStr("description"))
                .contains("书籍 CRUD");
        assertThat(tools.getJSONObject(1).getJSONObject("function").getJSONObject("parameters")
                .getJSONObject("properties").getJSONObject("operation").getJSONArray("enum"))
                .containsExactly("create", "list", "update", "delete");
        JSONObject bookCrudProperties = tools.getJSONObject(1).getJSONObject("function").getJSONObject("parameters")
                .getJSONObject("properties");
        assertThat(bookCrudProperties.containsKey("ownerUserId")).isTrue();
        assertThat(bookCrudProperties.containsKey("projectId")).isTrue();
        assertThat(bookCrudProperties.containsKey("title")).isTrue();
        assertThat(bookCrudProperties.containsKey("summary")).isTrue();
        assertThat(bookCrudProperties.containsKey("status")).isTrue();
        JSONArray oneOf = tools.getJSONObject(1).getJSONObject("function").getJSONObject("parameters")
                .getJSONArray("oneOf");
        assertThat(oneOf).hasSize(4);
        assertThat(oneOf.getJSONObject(0).getBool("additionalProperties")).isEqualTo(Boolean.FALSE);
        assertThat(oneOf.getJSONObject(1).getBool("additionalProperties")).isEqualTo(Boolean.FALSE);
        assertThat(oneOf.getJSONObject(2).getBool("additionalProperties")).isEqualTo(Boolean.FALSE);
        assertThat(oneOf.getJSONObject(3).getBool("additionalProperties")).isEqualTo(Boolean.FALSE);
        assertThat(oneOf.getJSONObject(1).getJSONObject("properties").keySet()).containsExactly("operation");
        assertThat(oneOf.getJSONObject(3).getJSONObject("properties").keySet())
                .containsExactlyInAnyOrder("operation", "projectId");
    }

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_OMITS_TOOLS_AND_TOOL_CHOICE_WHEN_TOOL_LIST_IS_EMPTY() {
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(Map.of("role", "user", "content", "hello")),
                List.of(),
                "auto"
        );

        String requestBody = client.buildTurnRequestBody(request, "gpt-test");
        JSONObject root = AgentJsonCodec.parseObj(requestBody);

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
