package com.penmate.backend.application.agent.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.AgentModelRoutingService;
import com.penmate.backend.application.agent.llm.*;
import com.penmate.backend.domain.agent.model.AgentMessage;
import com.penmate.backend.domain.agent.model.AgentSessionContextSummary;
import com.penmate.backend.domain.agent.repository.AgentRepository;
import com.penmate.backend.domain.agent.repository.AgentSessionContextSummaryRepository;
import com.penmate.backend.domain.agent.run.model.LlmTokenUsage;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentContextCompressionServiceTest {
    private final AgentRepository messages = mock(AgentRepository.class);
    private final AgentSessionContextSummaryRepository summaries = mock(AgentSessionContextSummaryRepository.class);
    private final AgentModelRoutingService routing = mock(AgentModelRoutingService.class);
    private final AgentLlmInvocationService invocations = mock(AgentLlmInvocationService.class);
    private final JacksonJsonCodec json = new JacksonJsonCodec(new ObjectMapper());
    private final AgentContextCompressionService service = new AgentContextCompressionService(
            messages, summaries, routing, invocations, json);

    @Test
    void compresses_only_the_uncompressed_tail_with_the_current_creative_model_and_no_tools() {
        AgentSessionContextSummary previous = new AgentSessionContextSummary(
                20L, 10L, 30L, "{\"summary\":\"earlier\"}", 2, 4, 2, null);
        AgentLlmExecutionConfig config = AgentLlmExecutionConfig.builder()
                .modelConfigId(50L).providerCode("openai").modelName("gpt-test")
                .maxContextTokens(32_000).maxOutputTokens(2_000).build();
        when(summaries.find(20L)).thenReturn(previous);
        when(messages.listMessages(20L)).thenReturn(List.of(
                message(1, "user", "already compressed"),
                message(3, "user", "new request"),
                message(4, "tool", "ignored tool payload"),
                message(5, "assistant", "new answer")));
        when(routing.resolveExecutionConfig(30L, null, "trace-compress")).thenReturn(config);
        when(invocations.invokeBuffered(any(), eq(config))).thenReturn(new AgentLlmTurnResponse(
                "stop", "{\"summary\":\"compact\",\"decisions\":[],\"completed\":[],\"pending\":[],\"constraints\":[],\"importantFacts\":[]}",
                List.of(), "{}", new LlmTokenUsage(100, 20, 120)));
        when(summaries.upsert(any())).thenReturn(1);

        AgentSessionContextSummary saved = service.compress(10L, 20L, 30L, "trace-compress");

        assertThat(saved.cutoffMessageSeq()).isEqualTo(5);
        assertThat(saved.promptTokens()).isEqualTo(100);
        assertThat(saved.completionTokens()).isEqualTo(20);
        ArgumentCaptor<AgentLlmTurnRequest> request = ArgumentCaptor.forClass(AgentLlmTurnRequest.class);
        verify(invocations).invokeBuffered(request.capture(), eq(config));
        assertThat(request.getValue().tools()).isEmpty();
        assertThat(request.getValue().toolChoice()).isEqualTo("none");
        assertThat(request.getValue().messages().get(1).content())
                .contains("previousSummary", "earlier", "new request", "new answer")
                .doesNotContain("already compressed", "ignored tool payload");
        verify(summaries).upsert(saved);
    }

    private AgentMessage message(int seq, String role, String content) {
        AgentMessage message = new AgentMessage();
        message.setSeqNo(seq);
        message.setRole(role);
        message.setContentMd(content);
        return message;
    }
}
