package com.penmate.backend.interfaces.api.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.run.AgentRunRecoveryAppService;
import com.penmate.backend.application.agent.query.AgentRunHistoryQueryService;
import com.penmate.backend.application.agent.run.AgentRunRetryService;
import com.penmate.backend.application.agent.usecase.AgentConversationAppService;
import com.penmate.backend.application.agent.usecase.AgentSessionTokenUsageAppService;
import com.penmate.backend.application.agent.usecase.AgentTurnAppService;
import com.penmate.backend.application.agent.usecase.AgentTurnResult;
import com.penmate.backend.application.agent.prompt.SkillPromptRegistry;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.interfaces.api.agent.stream.AgentRunEventStreamService;
import com.penmate.backend.interfaces.api.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.List;
import java.time.Instant;
import java.security.Principal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AgentControllerRunContractTest {

    @Mock
    private AgentConversationAppService agentConversationAppService;
    @Mock
    private AgentRunRecoveryAppService agentRunRecoveryAppService;
    @Mock
    private AgentSessionTokenUsageAppService agentSessionTokenUsageAppService;
    @Mock
    private AgentTurnAppService agentTurnAppService;
    @Mock
    private AgentRunEventStreamService agentRunEventStreamService;
    @Mock
    private AgentRunRetryService agentRunRetryService;
    @Mock
    private AgentRunHistoryQueryService agentRunHistoryQueryService;
    @Mock
    private SkillPromptRegistry skillPromptRegistry;
    @InjectMocks
    private AgentController agentController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void create_turn_returns_active_run_and_does_not_dispatch_old_generation_workflow() throws Exception {
        String traceId = "trace-run-controller-1";
        when(agentTurnAppService.createTurn(eq(101L), eq(90001L), any(), eq(traceId)))
                .thenReturn(AgentTurnResult.forRun(90001L, 50001L, 70001L, "running", "created", 1L));

        mockMvc().perform(post("/api/v1/novels/101/agent/sessions/90001/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(principal("201"))
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userMessage", "Write a suspense opening.",
                                "activeSkills", List.of(),
                                "taskRequest", Map.of(
                                        "taskType", "WRITE",
                                        "chapterId", "30001",
                                        "selectedText", "selected text"
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.session.sessionId").value("90001"))
                .andExpect(jsonPath("$.data.activeRun.turnId").value("50001"))
                .andExpect(jsonPath("$.data.activeRun.runId").value("70001"))
                .andExpect(jsonPath("$.data.activeRun.runStatus").value("running"))
                .andExpect(jsonPath("$.data.activeRun.runPhase").value("created"))
                .andExpect(jsonPath("$.data.activeRun.latestSequence").value("1"))
                .andExpect(jsonPath("$.data.activeTask").doesNotExist());

        verify(agentTurnAppService).createTurn(eq(101L), eq(90001L), any(), eq(traceId));
    }

    @Test
    void open_run_stream_uses_larger_replay_cursor_and_not_old_generation_stream() throws Exception {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(1_000L);
        when(agentRunEventStreamService.openStream(70001L, 42L)).thenReturn(emitter);

        mockMvc().perform(get("/api/v1/novels/101/agent/runs/70001/stream")
                        .param("after", "41")
                        .header("Last-Event-ID", "42")
                        .header("Accept", "text/event-stream"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache, no-transform"))
                .andExpect(header().string("X-Accel-Buffering", "no"));

        verify(agentRunEventStreamService).openStream(70001L, 42L);
    }

    @Test
    void retry_terminal_run_returns_successor_contract() throws Exception {
        String traceId = "trace-run-retry-1";
        when(agentRunRetryService.retry(101L, 70001L, 201L, List.of(), traceId))
                .thenReturn(run(70002L, 70001L, "PENDING", "created", 1L));

        mockMvc().perform(post("/api/v1/novels/101/agent/runs/70001/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(principal("201"))
                        .header("X-Trace-Id", traceId)
                        .content("{\"activeSkills\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.turnId").value("50001"))
                .andExpect(jsonPath("$.data.runId").value("70002"))
                .andExpect(jsonPath("$.data.runStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.runPhase").value("created"))
                .andExpect(jsonPath("$.data.latestSequence").value("1"));

        verify(agentRunRetryService).retry(101L, 70001L, 201L, List.of(), traceId);
    }

    @Test
    void list_session_runs_returns_output_bound_to_the_exact_run() throws Exception {
        AgentRun run = run(70001L, null, "RUNNING", "executing", 8L);
        var output = new AgentRunHistoryQueryService.RunOutput(
                "current partial", 15L, null, "partial", Instant.parse("2026-07-21T10:00:00Z"));
        when(agentRunHistoryQueryService.list(101L, 90001L, 201L)).thenReturn(List.of(
                new AgentRunHistoryQueryService.RunHistory(run, output, List.of())));

        mockMvc().perform(get("/api/v1/novels/101/agent/sessions/90001/runs")
                        .principal(principal("201")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].runId").value("70001"))
                .andExpect(jsonPath("$.data[0].turnId").value("50001"))
                .andExpect(jsonPath("$.data[0].output.text").value("current partial"))
                .andExpect(jsonPath("$.data[0].output.offset").value(15))
                .andExpect(jsonPath("$.data[0].output.sequence").doesNotExist())
                .andExpect(jsonPath("$.data[0].output.state").value("partial"));
    }

    private AgentRun run(Long runId, Long predecessorRunId, String status, String phase, Long sequence) {
        return new AgentRun(runId, 101L, 90001L, 50001L, 201L, predecessorRunId,
                status, phase, null, null, null, null, 0L, 0, null, null, null,
                sequence, null, "trace-run-retry-1", null, null);
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(agentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Principal principal(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null);
    }
}
