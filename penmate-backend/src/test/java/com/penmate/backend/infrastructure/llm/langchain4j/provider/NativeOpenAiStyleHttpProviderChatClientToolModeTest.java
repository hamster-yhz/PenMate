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
import com.penmate.backend.application.agent.tool.definition.StoryBibleUpdateToolDefinition;
import com.penmate.backend.application.agent.tool.definition.TodoCrudToolDefinition;
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
                List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("hello")),
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
                List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("hello")),
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
        JSONObject bookCrudParameters = tools.getJSONObject(1).getJSONObject("function").getJSONObject("parameters");
        assertThat(bookCrudParameters.getJSONObject("properties").getJSONObject("operation").getJSONArray("enum"))
                .containsExactly("create", "list", "update", "delete");
        JSONObject bookCrudProperties = bookCrudParameters.getJSONObject("properties");
        assertThat(bookCrudProperties.containsKey("ownerUserId")).isTrue();
        assertThat(bookCrudProperties.containsKey("projectId")).isTrue();
        assertThat(bookCrudProperties.containsKey("title")).isTrue();
        assertThat(bookCrudProperties.containsKey("summary")).isTrue();
        assertThat(bookCrudProperties.containsKey("status")).isTrue();
        assertThat(bookCrudParameters.getJSONArray("required")).containsExactly("operation");
        assertThat(bookCrudParameters.getBool("additionalProperties")).isEqualTo(Boolean.FALSE);
        assertThat(bookCrudParameters.containsKey("oneOf")).isFalse();
    }

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_BUILDS_TURN_REQUEST_BODY_WITH_COMPLETE_LLM_TOOL_LIST() {
        AgentToolDefinitionSource definitionSource = new InMemoryAgentToolDefinitionSource(List.of(
                new ContextEnhancerToolDefinition(),
                new BookCrudToolDefinition(),
                new DraftGenerationToolDefinition(),
                new QualityReviewToolDefinition(),
                new RagQueryToolDefinition(),
                new StoryBibleUpdateToolDefinition(),
                new TodoCrudToolDefinition()
        ));
        List<AgentLlmToolSchema> schemas = definitionSource.listLlmSchemas();
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("hello")),
                schemas,
                "auto"
        );

        String requestBody = client.buildTurnRequestBody(request, "gpt-test");
        JSONObject root = AgentJsonCodec.parseObj(requestBody);
        JSONArray tools = root.getJSONArray("tools");

        assertThat(tools).hasSize(schemas.size());
        assertThat(tools)
                .extracting(item -> ((JSONObject) item).getJSONObject("function").getStr("name"))
                .containsExactlyElementsOf(schemas.stream().map(AgentLlmToolSchema::toolCode).toList());
        JSONObject qualityReviewParameters = tools.stream()
                .map(JSONObject.class::cast)
                .filter(tool -> "quality_review".equals(tool.getJSONObject("function").getStr("name")))
                .findFirst()
                .orElseThrow()
                .getJSONObject("function")
                .getJSONObject("parameters");
        JSONObject storyBibleUpdateParameters = tools.stream()
                .map(JSONObject.class::cast)
                .filter(tool -> "story_bible_update".equals(tool.getJSONObject("function").getStr("name")))
                .findFirst()
                .orElseThrow()
                .getJSONObject("function")
                .getJSONObject("parameters");

        assertThat(qualityReviewParameters.getJSONObject("properties").getJSONObject("draftText").getStr("pattern"))
                .isEqualTo(".*\\S.*");
        assertThat(storyBibleUpdateParameters.getJSONObject("properties").getJSONObject("entryKey").getStr("pattern"))
                .isEqualTo(".*\\S.*");
    }

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_BUILDS_PROVIDER_COMPATIBLE_TOP_LEVEL_FUNCTION_SCHEMAS_FOR_EXPOSED_TOOLS() {
        AgentToolDefinitionSource definitionSource = new InMemoryAgentToolDefinitionSource(List.of(
                new BookCrudToolDefinition(),
                new DraftGenerationToolDefinition(),
                new TodoCrudToolDefinition()
        ));
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("hello")),
                definitionSource.listLlmSchemas(),
                "auto"
        );

        String requestBody = client.buildTurnRequestBody(request, "gpt-test");
        JSONObject root = AgentJsonCodec.parseObj(requestBody);
        JSONArray tools = root.getJSONArray("tools");

        assertThat(tools).hasSize(3);
        for (Object toolObject : tools) {
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
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_OMITS_TOOLS_AND_TOOL_CHOICE_WHEN_TOOL_LIST_IS_EMPTY() {
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("hello")),
                List.of(),
                "auto"
        );

        String requestBody = client.buildTurnRequestBody(request, "gpt-test");
        JSONObject root = AgentJsonCodec.parseObj(requestBody);

        assertThat(root.getJSONArray("tools")).isNull();
        assertThat(root.getStr("tool_choice", null)).isNull();
    }

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_PROVIDER_CHAT_CLIENT_UPGRADES_SINGLE_STRUCTURED_OUTPUT_TOOL_TO_JSON_SCHEMA_RESPONSE_FORMAT() {
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("请分析请求")),
                List.of(preflightDecisionToolSchema()),
                "required"
        );

        String requestBody = new TestOpenAiClient().buildTurnRequestBodyForTest(request, "gpt-4.1");
        JSONObject root = AgentJsonCodec.parseObj(requestBody);
        Object responseFormat = root.get("response_format");

        assertThat(responseFormat).isNotNull().isInstanceOf(JSONObject.class);
        JSONObject responseFormatObject = (JSONObject) responseFormat;
        assertThat(responseFormatObject.getStr("type")).isEqualTo("json_schema");
        assertThat(responseFormatObject.get("json_schema")).isInstanceOf(JSONObject.class);
        JSONObject jsonSchemaObject = responseFormatObject.getJSONObject("json_schema");
        assertThat(jsonSchemaObject.getStr("name")).isEqualTo("submit_preflight_decision");
        assertThat(jsonSchemaObject.getBool("strict")).isEqualTo(Boolean.TRUE);
        assertThat(jsonSchemaObject.getJSONObject("schema").getJSONObject("properties").keySet())
                .contains(
                        "behaviorType",
                        "executionPromptProfile",
                        "includeStyleContext",
                        "includeRagContext",
                        "includeStoryBibleContext",
                        "intentTags",
                        "hardConstraints",
                        "enabledSkills",
                        "enabledTools",
                        "outputExpectation",
                        "needsApproval",
                        "needsStoryBibleUpdate",
                        "needsClarification",
                        "reasoningSummary"
                );
        assertThat(root.getJSONArray("tools")).isNull();
        assertThat(root.get("tool_choice")).isNull();
    }

    @Test
    void UT_INFRA_LLM_NATIVE_OPENAI_STYLE_HTTP_PROVIDER_CHAT_CLIENT_FORCES_SINGLE_STRUCTURED_OUTPUT_TOOL_CALL_WHEN_JSON_SCHEMA_RESPONSE_FORMAT_IS_NOT_SUPPORTED() {
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(com.penmate.backend.domain.agent.model.AgentLlmMessage.user("请分析请求")),
                List.of(preflightDecisionToolSchema()),
                "required"
        );

        String requestBody = client.buildTurnRequestBody(request, "gpt-test");
        JSONObject root = AgentJsonCodec.parseObj(requestBody);
        Object toolChoice = root.get("tool_choice");
        Object responseFormat = root.get("response_format");

        assertThat(responseFormat).isNull();
        assertThat(root.getJSONArray("tools")).hasSize(1);
        assertThat(toolChoice).isInstanceOf(JSONObject.class);
        JSONObject toolChoiceObject = (JSONObject) toolChoice;
        assertThat(toolChoiceObject.getStr("type")).isEqualTo("function");
        assertThat(toolChoiceObject.getJSONObject("function").getStr("name"))
                .isEqualTo("submit_preflight_decision");
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

    private AgentLlmToolSchema preflightDecisionToolSchema() {
        return new AgentLlmToolSchema(
                "submit_preflight_decision",
                "Return the preflight decision as structured Json only.",
                """
                        {
                          "type": "object",
                          "properties": {
                            "behaviorType": {
                              "type": "string",
                              "enum": ["WRITE", "REWRITE", "WORLD_BUILD", "QUESTION_ANSWER", "STORY_BIBLE_QUERY_CANDIDATE"]
                            },
                            "executionPromptProfile": {
                              "type": "string"
                            },
                            "includeStyleContext": {
                              "type": "boolean"
                            },
                            "includeRagContext": {
                              "type": "boolean"
                            },
                            "includeStoryBibleContext": {
                              "type": "boolean"
                            },
                            "intentTags": {
                              "type": "array",
                              "items": {
                                "type": "string",
                                "enum": ["DRAFT_GENERATION", "STORY_BIBLE_QUERY", "CONTINUITY_CHECK", "STYLE_ALIGNMENT", "RAG_LOOKUP", "TOOL_EXECUTION", "CLARIFICATION"]
                              }
                            },
                            "hardConstraints": {
                              "type": "array",
                              "items": {
                                "type": "string"
                              }
                            },
                            "enabledSkills": {
                              "type": "array",
                              "items": {
                                "type": "string"
                              }
                            },
                            "enabledTools": {
                              "type": "array",
                              "items": {
                                "type": "string"
                              }
                            },
                            "outputExpectation": {
                              "type": ["string", "null"]
                            },
                            "needsApproval": {
                              "type": "boolean"
                            },
                            "needsStoryBibleUpdate": {
                              "type": "boolean"
                            },
                            "needsClarification": {
                              "type": "boolean"
                            },
                            "reasoningSummary": {
                              "type": "string"
                            }
                          },
                          "required": [
                            "behaviorType",
                            "executionPromptProfile",
                            "includeStyleContext",
                            "includeRagContext",
                            "includeStoryBibleContext",
                            "intentTags",
                            "hardConstraints",
                            "enabledSkills",
                            "enabledTools",
                            "outputExpectation",
                            "needsApproval",
                            "needsStoryBibleUpdate",
                            "needsClarification",
                            "reasoningSummary"
                          ],
                          "additionalProperties": false
                        }
                        """
        );
    }

    private static final class TestOpenAiClient extends OpenAiProviderChatClient {

        public String buildTurnRequestBodyForTest(AgentLlmTurnRequest request, String modelName) {
            return super.buildTurnRequestBody(request, modelName);
        }
    }

    private static final class TestNativeClient extends NativeOpenAiStyleHttpProviderChatClient {

        @Override
        public boolean supports(String providerCode) {
            return true;
        }
    }
}
