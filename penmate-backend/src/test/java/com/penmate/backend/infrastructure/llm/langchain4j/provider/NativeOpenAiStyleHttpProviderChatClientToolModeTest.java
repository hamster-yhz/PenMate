package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.tool.definition.AgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.BookCrudToolDefinition;
import com.penmate.backend.application.agent.tool.definition.ContextEnhancerToolDefinition;
import com.penmate.backend.application.agent.tool.definition.DraftGenerationToolDefinition;
import com.penmate.backend.application.agent.tool.definition.InMemoryAgentToolDefinitionSource;
import com.penmate.backend.application.agent.tool.definition.QualityReviewToolDefinition;
import com.penmate.backend.application.agent.tool.definition.RagQueryToolDefinition;
import com.penmate.backend.application.agent.tool.definition.StoryBibleSearchToolDefinition;
import com.penmate.backend.application.agent.tool.definition.StoryBibleUpdateToolDefinition;
import com.penmate.backend.application.agent.tool.definition.TodoCrudToolDefinition;
import com.penmate.backend.application.agent.tool.definition.TodoPlannerToolDefinition;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NativeOpenAiStyleHttpProviderChatClientToolModeTest {

    private final TestNativeClient client = new TestNativeClient();

    @Test
    void extracts_turn_response_with_tool_calls() {
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
                          "arguments": "{\\"prompt\\":\\"hello\\"}"
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
        assertThat(response.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.id()).isEqualTo("call_1");
            assertThat(call.toolCode()).isEqualTo("context_enhancer");
            assertThat(call.argumentsJson()).isEqualTo("{\"prompt\":\"hello\"}");
        });
    }

    @Test
    void builds_turn_request_body_with_tools_and_auto_tool_choice() {
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(AgentLlmMessage.user("hello")),
                List.of(new AgentLlmToolSchema(
                        "context_enhancer",
                        "Add context",
                        """
                                {
                                  "type": "object",
                                  "properties": { "prompt": { "type": "string" } },
                                  "required": ["prompt"]
                                }
                                """
                )),
                "auto"
        );

        JSONObject root = AgentJsonCodec.parseObj(client.buildTurnRequestBody(request, "gpt-test"));
        JSONArray tools = root.getJSONArray("tools");
        JSONObject function = tools.getJSONObject(0).getJSONObject("function");

        assertThat(root.getStr("model")).isEqualTo("gpt-test");
        assertThat(root.getStr("tool_choice")).isEqualTo("auto");
        assertThat(root.getJSONArray("messages").getJSONObject(0).getStr("content")).isEqualTo("hello");
        assertThat(function.getStr("name")).isEqualTo("context_enhancer");
        assertThat(function.getJSONObject("parameters").getJSONObject("properties")
                .getJSONObject("prompt").getStr("type")).isEqualTo("string");
    }

    @Test
    void builds_multiple_tools_in_definition_order() {
        AgentToolDefinitionSource definitions = new InMemoryAgentToolDefinitionSource(List.of(
                new ContextEnhancerToolDefinition(),
                new BookCrudToolDefinition()
        ));
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(AgentLlmMessage.user("hello")),
                definitions.listLlmSchemas(),
                "auto"
        );

        JSONArray tools = AgentJsonCodec.parseObj(client.buildTurnRequestBody(request, "gpt-test"))
                .getJSONArray("tools");

        assertThat(tools).hasSize(2);
        assertThat(tools.getJSONObject(0).getJSONObject("function").getStr("name"))
                .isEqualTo("context_enhancer");
        assertThat(tools.getJSONObject(1).getJSONObject("function").getStr("name"))
                .isEqualTo("book_crud");
    }

    @Test
    void builds_complete_llm_tool_list_with_current_story_bible_contracts() {
        AgentToolDefinitionSource definitions = new InMemoryAgentToolDefinitionSource(List.of(
                new ContextEnhancerToolDefinition(),
                new BookCrudToolDefinition(),
                new DraftGenerationToolDefinition(),
                new QualityReviewToolDefinition(),
                new RagQueryToolDefinition(),
                new StoryBibleSearchToolDefinition(),
                new StoryBibleUpdateToolDefinition(),
                new TodoCrudToolDefinition(),
                new TodoPlannerToolDefinition()
        ));
        List<AgentLlmToolSchema> schemas = definitions.listLlmSchemas();
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(AgentLlmMessage.user("hello")), schemas, "auto");

        JSONArray tools = AgentJsonCodec.parseObj(client.buildTurnRequestBody(request, "gpt-test"))
                .getJSONArray("tools");

        assertThat(tools)
                .extracting(item -> ((JSONObject) item).getJSONObject("function").getStr("name"))
                .containsExactlyElementsOf(schemas.stream().map(AgentLlmToolSchema::toolCode).toList());
        JSONObject storyBibleUpdate = findParameters(tools, "story_bible_update");
        assertThat(storyBibleUpdate.getJSONObject("properties").getJSONObject("operation").getStr("const"))
                .isEqualTo("batch");
        assertThat(storyBibleUpdate.getJSONObject("properties").getJSONObject("operations").getInt("minItems"))
                .isEqualTo(1);
    }

    @Test
    void sanitizes_unsupported_top_level_function_schema_keywords() {
        AgentLlmToolSchema schema = new AgentLlmToolSchema(
                "custom_tool",
                "Custom tool",
                """
                        {
                          "type": "object",
                          "properties": { "value": { "type": "string" } },
                          "oneOf": [{ "required": ["value"] }],
                          "additionalProperties": false
                        }
                        """
        );

        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(AgentLlmMessage.user("hello")), List.of(schema), "auto");
        JSONObject parameters = findParameters(
                AgentJsonCodec.parseObj(client.buildTurnRequestBody(request, "gpt-test")).getJSONArray("tools"),
                "custom_tool"
        );

        assertThat(parameters.getStr("type")).isEqualTo("object");
        assertThat(parameters.containsKey("oneOf")).isFalse();
        assertThat(parameters.getJSONObject("properties").containsKey("value")).isTrue();
    }

    @Test
    void omits_tools_and_tool_choice_when_tool_list_is_empty() {
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(AgentLlmMessage.user("hello")), List.of(), "auto");

        JSONObject root = AgentJsonCodec.parseObj(client.buildTurnRequestBody(request, "gpt-test"));

        assertThat(root.getJSONArray("tools")).isNull();
        assertThat(root.getStr("tool_choice", null)).isNull();
    }

    @Test
    void extracts_multiple_tool_calls_in_order() {
        String responseBody = """
                {
                  "choices": [{
                    "finish_reason": "tool_calls",
                    "message": {
                      "content": "need tools",
                      "tool_calls": [
                        {"id":"call_1","type":"function","function":{"name":"context_enhancer","arguments":"{}"}},
                        {"id":"call_2","type":"function","function":{"name":"book_crud","arguments":"{}"}}
                      ]
                    }
                  }]
                }
                """;

        AgentLlmTurnResponse response = client.extractTurnResponse(responseBody);

        assertThat(response.toolCalls()).extracting(call -> call.toolCode())
                .containsExactly("context_enhancer", "book_crud");
        assertThat(response.toolCalls()).extracting(call -> call.id())
                .containsExactly("call_1", "call_2");
    }

    private JSONObject findParameters(JSONArray tools, String toolCode) {
        return tools.stream()
                .map(JSONObject.class::cast)
                .filter(tool -> toolCode.equals(tool.getJSONObject("function").getStr("name")))
                .findFirst()
                .orElseThrow()
                .getJSONObject("function")
                .getJSONObject("parameters");
    }

    private static final class TestNativeClient extends NativeOpenAiStyleHttpProviderChatClient {
        @Override
        public boolean supports(String providerCode) {
            return true;
        }
    }
}
