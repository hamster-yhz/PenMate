package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentReasoningPolicy;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload;
import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeProviderChatClientTest {

    private final ClaudeProviderChatClient client = new ClaudeProviderChatClient();
    private final AgentLlmExecutionConfig config = AgentLlmExecutionConfig.builder()
            .baseUrl("https://api.anthropic.com")
            .apiKey("test")
            .modelName("claude-sonnet-4-6")
            .maxOutputTokens(2048)
            .build();

    @Test
    void builds_messages_request_with_a_cache_breakpoint_and_native_tools() {
        AgentLlmMessage assistant = AgentLlmMessage.assistant("",
                List.of(new AgentLlmToolCallPayload("call-1", "function", "story_search", "{\"query\":\"Mira\"}")));
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(List.of(
                AgentLlmMessage.system("stable"),
                AgentLlmMessage.system("dynamic"),
                AgentLlmMessage.user("find"),
                assistant,
                AgentLlmMessage.tool("call-1", "found")),
                List.of(new AgentLlmToolSchema("story_search", "Search", "{\"type\":\"object\"}")), "auto");

        JSONObject body = AgentJsonCodec.parseObj(client.buildRequestBody(request, config));
        JSONArray system = body.getJSONArray("system");
        JSONArray messages = body.getJSONArray("messages");

        assertThat(system.getJSONObject(0).getJSONObject("cache_control").getStr("type"))
                .isEqualTo("ephemeral");
        assertThat(system.getJSONObject(1).containsKey("cache_control")).isFalse();
        assertThat(body.getJSONArray("tools").getJSONObject(0).getJSONObject("input_schema").getStr("type"))
                .isEqualTo("object");
        assertThat(messages.getJSONObject(1).getStr("role")).isEqualTo("assistant");
        assertThat(messages.getJSONObject(2).getStr("role")).isEqualTo("user");
        assertThat(messages.getJSONObject(2).getJSONArray("content").getJSONObject(0).getStr("type"))
                .isEqualTo("tool_result");
    }

    @Test
    void extracts_tool_calls_and_anthropic_cache_usage() {
        var response = client.extractTurnResponse("""
                {
                  "stop_reason":"tool_use",
                  "content":[
                    {"type":"text","text":"Checking"},
                    {"type":"tool_use","id":"call-1","name":"story_search","input":{"query":"Mira"}}
                  ],
                  "usage":{
                    "input_tokens":8,
                    "cache_read_input_tokens":100,
                    "cache_creation_input_tokens":20,
                    "output_tokens":5
                  }
                }
                """);

        assertThat(response.finishReason()).isEqualTo("tool_calls");
        assertThat(response.assistantText()).isEqualTo("Checking");
        assertThat(response.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.id()).isEqualTo("call-1");
            assertThat(call.argumentsJson()).contains("Mira");
        });
        assertThat(response.tokenUsage()).isEqualTo(new LlmTokenUsage(128, 5, 133, 100, 20));
    }

    @Test
    void falls_back_to_a_tool_cache_breakpoint_when_system_is_absent() {
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(AgentLlmMessage.user("find")),
                List.of(new AgentLlmToolSchema("story_search", "Search", "{\"type\":\"object\"}")),
                "auto");

        JSONObject body = AgentJsonCodec.parseObj(client.buildRequestBody(request, config));

        assertThat(body.getJSONArray("tools").getJSONObject(0)
                .getJSONObject("cache_control").getStr("type")).isEqualTo("ephemeral");
    }

    @Test
    void falls_back_to_the_first_message_when_system_and_tools_are_absent() {
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(AgentLlmMessage.user("stable prefix")), List.of(), "none");

        JSONObject body = AgentJsonCodec.parseObj(client.buildRequestBody(request, config));

        assertThat(body.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
                .getJSONObject(0).getJSONObject("cache_control").getStr("type"))
                .isEqualTo("ephemeral");
    }

    @Test
    void omits_tools_when_tool_choice_is_none() {
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(AgentLlmMessage.user("plain response")),
                List.of(new AgentLlmToolSchema("story_search", "Search", "{\"type\":\"object\"}")),
                "none");

        JSONObject body = AgentJsonCodec.parseObj(client.buildRequestBody(request, config));

        assertThat(body.containsKey("tools")).isFalse();
        assertThat(body.containsKey("tool_choice")).isFalse();
    }

    @Test
    void sends_adaptive_thinking_effort_and_round_trips_signed_thinking_blocks() {
        AgentLlmExecutionConfig reasoningConfig = config.toBuilder()
                .reasoningPolicy(new AgentReasoningPolicy("max", "auto", "adaptive"))
                .build();
        var response = client.extractTurnResponse("""
                {
                  "content":[
                    {"type":"thinking","thinking":"private","signature":"signed-value"},
                    {"type":"tool_use","id":"call-1","name":"story_search","input":{"query":"Mira"}}
                  ],
                  "usage":{"input_tokens":8,"output_tokens":5}
                }
                """);
        AgentLlmMessage assistant = AgentLlmMessage.assistant("",
                List.of(new AgentLlmToolCallPayload("call-1", "function", "story_search", "{\"query\":\"Mira\"}")),
                response.providerItems());
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(List.of(
                AgentLlmMessage.user("find"), assistant, AgentLlmMessage.tool("call-1", "found")),
                List.of(), "auto");

        JSONObject body = AgentJsonCodec.parseObj(client.buildRequestBody(request, reasoningConfig));
        JSONObject thinking = body.getJSONArray("messages").getJSONObject(1)
                .getJSONArray("content").getJSONObject(0);

        assertThat(body.getJSONObject("thinking").getStr("type")).isEqualTo("adaptive");
        assertThat(body.getJSONObject("output_config").getStr("effort")).isEqualTo("max");
        assertThat(response.providerItems()).hasSize(1);
        assertThat(thinking.getStr("type")).isEqualTo("thinking");
        assertThat(thinking.getStr("signature")).isEqualTo("signed-value");
    }
}
