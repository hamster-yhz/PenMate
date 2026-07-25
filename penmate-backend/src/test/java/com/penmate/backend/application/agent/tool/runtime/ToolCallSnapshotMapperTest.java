package com.penmate.backend.application.agent.tool.runtime;

import com.penmate.backend.domain.agent.model.AgentLlmMessage;
import com.penmate.backend.domain.agent.model.AgentLlmMessageRole;
import com.penmate.backend.domain.agent.model.AgentLlmToolCallPayload;
import com.penmate.backend.domain.agent.model.AgentLlmProviderItem;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCallSnapshotMapperTest {

    private final ToolCallSnapshotMapper toolCallSnapshotMapper =
            new ToolCallSnapshotMapper(new JacksonJsonCodec(new ObjectMapper()));

    @Test
    void UT_APP_AGENT_TOOL_CALL_SNAPSHOT_MAPPER_SHOULD_PARSE_PERSISTED_ASSISTANT_TOOL_CALL_AND_TOOL_RESULT_MESSAGES_TO_TYPED_MODELS() {
        String rawMessagesJson = """
                [
                  {
                    "role": "user",
                    "content": "list books"
                  },
                  {
                    "role": "assistant",
                    "content": "need tool",
                    "tool_calls": [
                      {
                        "id": "call_1",
                        "type": "function",
                        "function": {
                          "name": "book_crud",
                          "arguments": "{\\\"operation\\\":\\\"list\\\"}"
                        }
                      }
                    ]
                  },
                  {
                    "role": "tool",
                    "tool_call_id": "call_1",
                    "content": "{\\\"items\\\":[]}"
                  }
                ]
                """;

        List<AgentLlmMessage> messages = toolCallSnapshotMapper.parseMessagesToTyped(rawMessagesJson);

        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).role()).isEqualTo(AgentLlmMessageRole.USER);
        assertThat(messages.get(0).content()).isEqualTo("list books");

        assertThat(messages.get(1).role()).isEqualTo(AgentLlmMessageRole.ASSISTANT);
        assertThat(messages.get(1).content()).isEqualTo("need tool");
        assertThat(messages.get(1).toolCalls()).hasSize(1);
        AgentLlmToolCallPayload toolCallPayload = messages.get(1).toolCalls().getFirst();
        assertThat(toolCallPayload.id()).isEqualTo("call_1");
        assertThat(toolCallPayload.type()).isEqualTo("function");
        assertThat(toolCallPayload.functionName()).isEqualTo("book_crud");
        assertThat(toolCallPayload.argumentsJson()).isEqualTo("{\"operation\":\"list\"}");

        assertThat(messages.get(2).role()).isEqualTo(AgentLlmMessageRole.TOOL);
        assertThat(messages.get(2).toolCallId()).isEqualTo("call_1");
        assertThat(messages.get(2).content()).isEqualTo("{\"items\":[]}");
    }

    @Test
    void UT_APP_AGENT_TOOL_CALL_SNAPSHOT_MAPPER_SHOULD_SERIALIZE_TYPED_ASSISTANT_TOOL_CALL_AND_TOOL_RESULT_MESSAGES_WITH_PERSISTED_FIELD_NAMES() {
        List<AgentLlmMessage> messages = List.of(
                AgentLlmMessage.user("list books"),
                AgentLlmMessage.assistant(
                        "need tool",
                        List.of(new AgentLlmToolCallPayload(
                                "call_1",
                                "function",
                                "book_crud",
                                "{\"operation\":\"list\"}"
                        ))
                ),
                AgentLlmMessage.tool("call_1", "{\"items\":[]}")
        );

        String rawMessagesJson = toolCallSnapshotMapper.toConversationMessagesJson(messages);

        assertThat(rawMessagesJson).contains("\"tool_calls\"");
        assertThat(rawMessagesJson).contains("\"tool_call_id\"");
        assertThat(rawMessagesJson).contains("\"name\":\"book_crud\"");
        assertThat(rawMessagesJson).contains("\"arguments\":\"{\\\"operation\\\":\\\"list\\\"}\"");
    }

    @Test
    void round_trips_opaque_provider_items_for_reasoning_tool_continuation() {
        AgentLlmProviderItem item = new AgentLlmProviderItem("OPENAI_RESPONSES",
                "{\"type\":\"reasoning\",\"encrypted_content\":\"opaque\"}");
        List<AgentLlmMessage> source = List.of(AgentLlmMessage.assistant(
                "", List.of(new AgentLlmToolCallPayload("call_1", "function", "book_crud", "{}")),
                List.of(item)));

        List<AgentLlmMessage> restored = toolCallSnapshotMapper.parseMessagesToTyped(
                toolCallSnapshotMapper.toConversationMessagesJson(source));

        assertThat(restored).singleElement().satisfies(message -> {
            assertThat(message.providerItems()).containsExactly(item);
            assertThat(message.toolCalls()).hasSize(1);
        });
    }
}
