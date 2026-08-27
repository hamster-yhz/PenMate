package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmStreamEvent;
import com.penmate.backend.application.agent.llm.AgentLlmStreamObserver;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTransientException;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.application.agent.llm.AgentLlmTurnResponse;
import com.penmate.backend.application.agent.llm.AgentReasoningPolicy;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmProviderItem;
import com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiResponsesProviderChatClientTest {

    private final OpenAiResponsesProviderChatClient client = new OpenAiResponsesProviderChatClient();
    private final AgentLlmExecutionConfig config = AgentLlmExecutionConfig.builder()
            .providerCode("openai")
            .protocolCode("OPENAI_RESPONSES")
            .baseUrl("https://api.openai.com/v1")
            .apiKey("sk-test")
            .modelName("gpt-5")
            .reasoningPolicy(new AgentReasoningPolicy("high", "detailed", "pro"))
            .maxOutputTokens(4321)
            .build();

    @Test
    void builds_responses_request_with_reasoning_and_flat_function_tools() {
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(AgentLlmMessage.user("检查章节")),
                List.of(new AgentLlmToolSchema("story_search", "Search story facts",
                        "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}")),
                "auto");

        JSONObject body = AgentJsonCodec.parseObj(client.buildRequestBody(request, config, true, true));
        JSONObject tool = body.getJSONArray("tools").getJSONObject(0);

        assertThat(body.getBool("stream")).isTrue();
        assertThat(body.getBool("store")).isFalse();
        assertThat(body.getInt("max_output_tokens")).isEqualTo(4321);
        assertThat(body.getJSONObject("reasoning").getStr("effort")).isEqualTo("high");
        assertThat(body.getJSONObject("reasoning").getStr("summary")).isEqualTo("detailed");
        assertThat(body.getJSONObject("reasoning").getStr("mode")).isEqualTo("pro");
        assertThat(tool.getStr("type")).isEqualTo("function");
        assertThat(tool.getStr("name")).isEqualTo("story_search");
        assertThat(tool.getJSONObject("parameters").getStr("type")).isEqualTo("object");
    }

    @Test
    void sends_explicit_none_effort_when_reasoning_is_disabled() {
        AgentLlmExecutionConfig disabledConfig = config.toBuilder()
                .reasoningPolicy(AgentReasoningPolicy.DISABLED)
                .build();
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(AgentLlmMessage.user("Answer directly.")), List.of(), "auto");

        JSONObject body = AgentJsonCodec.parseObj(
                client.buildRequestBody(request, disabledConfig, false, true));

        assertThat(body.getJSONObject("reasoning").getStr("effort")).isEqualTo("none");
        assertThat(body.getJSONObject("reasoning").containsKey("mode")).isFalse();
        assertThat(body.getJSONObject("reasoning").containsKey("summary")).isFalse();
    }

    @Test
    void round_trips_opaque_response_items_before_function_outputs() {
        AgentLlmProviderItem reasoning = new AgentLlmProviderItem("OPENAI_RESPONSES",
                "{\"type\":\"reasoning\",\"id\":\"rs_1\",\"encrypted_content\":\"opaque\",\"summary\":[]}");
        AgentLlmProviderItem commentary = new AgentLlmProviderItem("OPENAI_RESPONSES",
                "{\"type\":\"message\",\"id\":\"msg_1\",\"phase\":\"commentary\",\"content\":[{\"type\":\"output_text\",\"text\":\"working\"}]}");
        AgentLlmProviderItem functionCall = new AgentLlmProviderItem("OPENAI_RESPONSES",
                "{\"type\":\"function_call\",\"id\":\"fc_1\",\"status\":\"completed\",\"call_id\":\"call_1\",\"name\":\"story_search\",\"arguments\":\"{}\"}");
        AgentLlmMessage assistant = AgentLlmMessage.assistant("",
                List.of(new AgentLlmToolCallPayload("call_1", "function", "story_search", "{}")),
                List.of(reasoning, commentary, functionCall));
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(List.of(
                AgentLlmMessage.user("检查"), assistant, AgentLlmMessage.tool("call_1", "找到设定")),
                List.of(), "auto");

        JSONArray input = AgentJsonCodec.parseObj(client.buildRequestBody(request, config, false, true))
                .getJSONArray("input");

        assertThat(input).hasSize(4);
        assertThat(input.getJSONObject(1).getStr("type")).isEqualTo("reasoning");
        assertThat(input.getJSONObject(1).getStr("encrypted_content")).isEqualTo("opaque");
        assertThat(input.getJSONObject(2).getStr("type")).isEqualTo("function_call");
        assertThat(input.getJSONObject(2).containsKey("phase")).isFalse();
        assertThat(input.getJSONObject(2).containsKey("status")).isFalse();
        assertThat(input.getJSONObject(3).getStr("type")).isEqualTo("function_call_output");
        assertThat(input.getJSONObject(3).getStr("call_id")).isEqualTo("call_1");
        assertThat(input.toString()).doesNotContain("working", "phase");
    }

    @Test
    void extracts_public_process_blocks_tools_usage_and_provider_items() {
        AgentLlmTurnResponse response = client.extractTurnResponse("""
                {
                  "id":"resp_1",
                  "status":"completed",
                  "output":[
                    {"id":"rs_1","type":"reasoning","encrypted_content":"opaque","summary":[{"type":"summary_text","text":"发现冲突"},{"type":"summary_text","text":"检查修复方案"}]},
                    {"id":"msg_1","type":"message","phase":"commentary","content":[{"type":"output_text","text":"正在检查设定"}]},
                    {"id":"fc_1","type":"function_call","call_id":"call_1","name":"story_search","arguments":"{\\"query\\":\\"Mira\\"}"}
                  ],
                  "usage":{"input_tokens":10,"output_tokens":7,"total_tokens":17,"input_tokens_details":{"cached_tokens":2},"output_tokens_details":{"reasoning_tokens":4}}
                }
                """);

        assertThat(response.assistantText()).isEmpty();
        assertThat(response.commentaryText()).isEqualTo("正在检查设定");
        assertThat(response.reasoningSummary()).isEqualTo("发现冲突\n\n检查修复方案");
        assertThat(response.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.id()).isEqualTo("call_1");
            assertThat(call.toolCode()).isEqualTo("story_search");
        });
        assertThat(response.providerItems()).hasSize(3);
        assertThat(response.tokenUsage().cachedPromptTokens()).isEqualTo(2);
        assertThat(response.tokenUsage().reasoningTokens()).isEqualTo(4);
    }

    @Test
    void streams_commentary_reasoning_and_final_text_as_distinct_events() throws Exception {
        List<AgentLlmStreamEvent> events = new ArrayList<>();
        AgentLlmTurnResponse response = client.readResponsesEventStream(
                new BufferedReader(new StringReader("""
                        data: {"type":"response.output_item.added","output_index":0,"item":{"id":"msg_c","type":"message","phase":"commentary","content":[]}}

                        data: {"type":"response.output_text.delta","output_index":0,"delta":"正在检查"}

                        data: {"type":"response.reasoning_summary_text.delta","delta":"发现冲突"}

                        data: {"type":"response.output_item.added","output_index":1,"item":{"id":"msg_f","type":"message","phase":"final_answer","content":[]}}

                        data: {"type":"response.output_text.delta","output_index":1,"delta":"已完成"}

                        data: [DONE]

                        """)), new CollectingObserver(events));

        assertThat(events).containsExactly(
                new AgentLlmStreamEvent.CommentaryDelta("正在检查"),
                new AgentLlmStreamEvent.ReasoningSummaryDelta("发现冲突"),
                new AgentLlmStreamEvent.OutputTextDelta("已完成"));
        assertThat(response.commentaryText()).isEqualTo("正在检查");
        assertThat(response.reasoningSummary()).isEqualTo("发现冲突");
        assertThat(response.assistantText()).isEqualTo("已完成");
    }

    @Test
    void preserves_streamed_message_phases_when_proxy_completed_items_omit_them() throws Exception {
        List<AgentLlmStreamEvent> events = new ArrayList<>();
        AgentLlmTurnResponse response = client.readResponsesEventStream(
                new BufferedReader(new StringReader("""
                        data: {"type":"response.output_item.added","output_index":0,"item":{"id":"msg_c","type":"message","phase":"commentary","content":[]}}

                        data: {"type":"response.output_text.delta","output_index":0,"delta":"正在检查"}

                        data: {"type":"response.output_item.added","output_index":1,"item":{"id":"msg_f","type":"message","phase":"final_answer","content":[]}}

                        data: {"type":"response.output_text.delta","output_index":1,"delta":"最终回答"}

                        data: {"type":"response.completed","response":{"id":"resp_1","status":"completed","output":[{"id":"msg_c","type":"message","content":[{"type":"output_text","text":"正在检查"}]},{"id":"msg_f","type":"message","content":[{"type":"output_text","text":"最终回答"}]}],"usage":{"input_tokens":2,"output_tokens":4,"total_tokens":6}}}

                        data: [DONE]

                        """)), new CollectingObserver(events));

        assertThat(response.commentaryText()).isEqualTo("正在检查");
        assertThat(response.assistantText()).isEqualTo("最终回答");
        assertThat(response.tokenUsage().totalTokens()).isEqualTo(6);
    }

    @Test
    void classifies_unknown_phase_text_as_commentary_when_the_response_calls_a_tool() throws Exception {
        List<AgentLlmStreamEvent> events = new ArrayList<>();
        AgentLlmTurnResponse response = client.readResponsesEventStream(
                new BufferedReader(new StringReader("""
                        data: {"type":"response.output_item.added","output_index":0,"item":{"id":"msg_1","type":"message","content":[]}}

                        data: {"type":"response.output_text.delta","output_index":0,"delta":"working"}

                        data: {"type":"response.output_item.added","output_index":1,"item":{"id":"fc_1","type":"function_call","call_id":"call_1","name":"story_search","arguments":"{}"}}

                        data: [DONE]

                        """)), new CollectingObserver(events));

        assertThat(events).containsExactly(new AgentLlmStreamEvent.CommentaryDelta("working"));
        assertThat(response.commentaryText()).isEqualTo("working");
        assertThat(response.assistantText()).isEmpty();
        assertThat(response.toolCalls()).hasSize(1);
    }

    @Test
    void classifies_unknown_phase_text_as_final_when_the_response_has_no_tool_call() throws Exception {
        List<AgentLlmStreamEvent> events = new ArrayList<>();
        AgentLlmTurnResponse response = client.readResponsesEventStream(
                new BufferedReader(new StringReader("""
                        data: {"type":"response.output_item.added","output_index":0,"item":{"id":"msg_1","type":"message","content":[]}}

                        data: {"type":"response.output_text.delta","output_index":0,"delta":"answer"}

                        data: [DONE]

                        """)), new CollectingObserver(events));

        assertThat(events).containsExactly(new AgentLlmStreamEvent.OutputTextDelta("answer"));
        assertThat(response.assistantText()).isEqualTo("answer");
        assertThat(response.commentaryText()).isEmpty();
    }

    @Test
    void buffered_extraction_uses_the_same_unknown_phase_rule_as_streaming() {
        AgentLlmTurnResponse response = client.extractTurnResponse("""
                {
                  "status":"completed",
                  "output":[
                    {"type":"message","content":[{"type":"output_text","text":"working"}]},
                    {"type":"function_call","call_id":"call_1","name":"story_search","arguments":"{}"}
                  ]
                }
                """);

        assertThat(response.assistantText()).isEmpty();
        assertThat(response.commentaryText()).isEqualTo("working");
    }

    @Test
    void classifies_upstream_stream_read_errors_as_transient() {
        assertThatThrownBy(() -> client.readResponsesEventStream(
                new BufferedReader(new StringReader("""
                        data: {"type":"error","sequence_number":0,"error":{"type":"upstream_error","message":"stream_read_error","code":"stream_read_error"}}

                        """)), new CollectingObserver(new ArrayList<>())))
                .isInstanceOf(AgentLlmTransientException.class)
                .hasMessageContaining("stream_read_error");
    }

    @Test
    void keeps_invalid_request_stream_errors_terminal() {
        assertThatThrownBy(() -> client.readResponsesEventStream(
                new BufferedReader(new StringReader("""
                        data: {"type":"response.failed","response":{"error":{"type":"invalid_request_error","code":"invalid_request"}}}

                        """)), new CollectingObserver(new ArrayList<>())))
                .isInstanceOf(com.penmate.backend.application.common.exception.BusinessException.class)
                .isNotInstanceOf(AgentLlmTransientException.class);
    }

    private static final class CollectingObserver implements AgentLlmStreamObserver {
        private final List<AgentLlmStreamEvent> events;

        private CollectingObserver(List<AgentLlmStreamEvent> events) {
            this.events = events;
        }

        @Override public void onResponseStarted() {}
        @Override public void onTextDelta(String text) { events.add(new AgentLlmStreamEvent.OutputTextDelta(text)); }
        @Override public void onEvent(AgentLlmStreamEvent event) { events.add(event); }
        @Override public void onCancellable(Runnable cancelAction) {}
        @Override public boolean isCancelled() { return false; }
    }
}
