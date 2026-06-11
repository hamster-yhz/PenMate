package com.penmate.backend.interfaces.api.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.agent.run.AgentRunRecoveryAppService;
import com.penmate.backend.application.agent.usecase.AgentConversationAppService;
import com.penmate.backend.application.agent.usecase.AgentSessionTokenUsageAppService;
import com.penmate.backend.application.agent.usecase.AgentTurnAppService;
import com.penmate.backend.application.agent.usecase.AgentTurnResult;
import com.penmate.backend.infrastructure.realtime.AgentRunEventStreamService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    @InjectMocks
    private AgentController agentController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void create_turn_returns_active_run_and_does_not_dispatch_old_generation_workflow() throws Exception {
        String traceId = "trace-run-controller-1";
        when(agentTurnAppService.createTurn(eq(101L), eq(90001L), any(), eq(traceId)))
                .thenReturn(new AgentTurnResult(null, new AgentTurnResult.ActiveRunView(50001L, 70001L, "running")));

        mockMvc().perform(post("/api/v1/novels/101/agent/sessions/90001/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "operatorId", "201",
                                "userMessage", "Write a suspense opening.",
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
                .andExpect(status().isOk());

        verify(agentRunEventStreamService).openStream(70001L, 42L);
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(agentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
