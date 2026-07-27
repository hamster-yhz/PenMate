package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.llm.AgentLlmExecutionConfig;
import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderPromptCachingRequestTest {

    private final AgentLlmExecutionConfig config = AgentLlmExecutionConfig.builder()
            .providerCode("openai")
            .protocolCode("OPENAI_RESPONSES")
            .baseUrl("https://api.openai.com/v1")
            .apiKey("sk-test")
            .modelName("gpt-5.6-sol")
            .build();
    private final AgentLlmTurnRequest request = new AgentLlmTurnRequest(
            List.of(AgentLlmMessage.system("stable"), AgentLlmMessage.user("write")),
            List.of(new AgentLlmToolSchema("story_search", "Search", "{\"type\":\"object\"}")),
            "auto");

    @Test
    void responses_marks_the_stable_system_block_and_sends_a_cache_key() {
        JSONObject body = AgentJsonCodec.parseObj(
                new OpenAiResponsesProviderChatClient().buildRequestBody(request, config, true, true));
        JSONObject system = body.getJSONArray("input").getJSONObject(0);
        JSONObject block = system.getJSONArray("content").getJSONObject(0);

        assertThat(body.getStr("prompt_cache_key")).hasSize(64);
        assertThat(body.getJSONObject("prompt_cache_options").getStr("mode")).isEqualTo("explicit");
        assertThat(block.getStr("type")).isEqualTo("input_text");
        assertThat(block.getJSONObject("prompt_cache_breakpoint").getStr("mode")).isEqualTo("explicit");
    }

    @Test
    void chat_completions_marks_the_stable_system_block_and_requests_stream_usage() {
        OpenAiProviderChatClient client = new OpenAiProviderChatClient();
        JSONObject body = AgentJsonCodec.parseObj(client.buildTurnRequestBody(
                request, config.modelName(), config, "/chat/completions"));
        JSONArray content = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content");

        assertThat(body.getStr("prompt_cache_key")).hasSize(64);
        assertThat(content.getJSONObject(0).getJSONObject("prompt_cache_breakpoint").getStr("mode"))
                .isEqualTo("explicit");

        JSONObject streaming = AgentJsonCodec.parseObj(client.buildStreamingTurnRequestBody(
                request, config.modelName(), config, "/chat/completions"));
        assertThat(streaming.getJSONObject("stream_options").getBool("include_usage")).isTrue();
    }

    @Test
    void compatible_usage_maps_openai_writes_deepseek_hits_and_reasoning() {
        TestNativeClient client = new TestNativeClient();
        var response = client.extractTurnResponse("""
                {
                  "choices":[{"finish_reason":"stop","message":{"content":"ok"}}],
                  "usage":{
                    "prompt_tokens":120,
                    "completion_tokens":8,
                    "total_tokens":128,
                    "prompt_cache_hit_tokens":64,
                    "prompt_tokens_details":{"cache_write_tokens":32},
                    "completion_tokens_details":{"reasoning_tokens":4}
                  }
                }
                """);

        assertThat(response.tokenUsage()).isEqualTo(new LlmTokenUsage(120, 8, 128, 64, 32, 4));
    }

    @Test
    void compatible_usage_maps_gemini_total_cached_tokens() {
        TestNativeClient client = new TestNativeClient();
        var response = client.extractTurnResponse("""
                {
                  "choices":[{"finish_reason":"stop","message":{"content":"ok"}}],
                  "usage":{
                    "prompt_tokens":120,
                    "completion_tokens":8,
                    "total_tokens":128,
                    "total_cached_tokens":64
                  }
                }
                """);

        assertThat(response.tokenUsage()).isEqualTo(new LlmTokenUsage(120, 8, 128, 64, 0));
    }

    private static final class TestNativeClient extends NativeOpenAiStyleHttpProviderChatClient {
        @Override
        public boolean supports(String providerCode) {
            return true;
        }
    }
}
