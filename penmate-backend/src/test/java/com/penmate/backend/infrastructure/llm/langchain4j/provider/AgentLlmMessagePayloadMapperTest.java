package com.penmate.backend.infrastructure.llm.langchain4j.provider;

import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLlmMessagePayloadMapperTest {

    private final AgentLlmMessagePayloadMapper mapper = new AgentLlmMessagePayloadMapper();

    @Test
    void should_convert_typed_messages_to_openai_style_payload() {
        List<Map<String, Object>> payload = mapper.toProviderMessages(List.of(
                AgentLlmMessage.system("你是执行代理"),
                AgentLlmMessage.user("上一轮提问"),
                AgentLlmMessage.assistant("", List.of(new AgentLlmToolCallPayload(
                        "call_1",
                        "function",
                        "context_enhancer",
                        "{\"prompt\":\"补充\"}"
                ))),
                AgentLlmMessage.tool("call_1", "{\"context\":\"补充背景\"}")
        ));

        assertThat(payload).hasSize(4);
        assertThat(payload.get(0)).containsEntry("role", "system").containsEntry("content", "你是执行代理");
        assertThat(payload.get(2)).containsEntry("role", "assistant");
        assertThat((List<?>) payload.get(2).get("tool_calls")).hasSize(1);
        assertThat(payload.get(3)).containsEntry("role", "tool")
                .containsEntry("tool_call_id", "call_1")
                .containsEntry("content", "{\"context\":\"补充背景\"}");
    }
}
