package com.penmate.backend.domain.agent.model;

import com.penmate.backend.application.agent.llm.AgentLlmToolSchema;
import com.penmate.backend.application.agent.llm.AgentLlmTurnRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentLlmMessageTest {

    @Test
    void should_create_assistant_tool_call_and_tool_result_messages() {
        AgentLlmToolCallPayload payload = new AgentLlmToolCallPayload(
                "call_1",
                "function",
                "custom_tool",
                "{\"prompt\":\"补充上下文\"}"
        );

        AgentLlmMessage assistant = AgentLlmMessage.assistant("", List.of(payload));
        AgentLlmMessage tool = AgentLlmMessage.tool("call_1", "{\"context\":\"补充背景设定\"}");

        assertThat(assistant.toolCalls()).containsExactly(payload);
        assertThat(tool.role()).isEqualTo(AgentLlmMessageRole.TOOL);
        assertThat(tool.toolCallId()).isEqualTo("call_1");
        assertThat(tool.content()).isEqualTo("{\"context\":\"补充背景设定\"}");
    }

    @Test
    void should_reject_tool_message_without_tool_call_id() {
        assertThatThrownBy(() -> new AgentLlmMessage(
                AgentLlmMessageRole.TOOL,
                "{}",
                List.of(),
                "  "
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toolCallId is required for tool message");
    }

    @Test
    void should_make_turn_request_messages_immutable_and_default_tool_choice() {
        AgentLlmMessage user = AgentLlmMessage.user("当前请求");
        AgentLlmTurnRequest request = new AgentLlmTurnRequest(
                List.of(user),
                List.of(new AgentLlmToolSchema("tool_a", "desc", "{\"type\":\"object\"}")),
                null
        );

        assertThat(request.messages()).containsExactly(user);
        assertThat(request.toolChoice()).isEqualTo("auto");
        assertThatThrownBy(() -> request.messages().add(AgentLlmMessage.user("x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
