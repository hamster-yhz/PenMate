package com.penmate.backend.application.agent.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.agent.run.model.AgentRun;
import com.penmate.backend.domain.agent.run.model.AgentToolCallExecution;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import com.penmate.backend.domain.agent.run.repository.AgentToolCallExecutionRepository;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRunRecoveryPromptServiceTest {

    private final AgentRunRepository runs = mock(AgentRunRepository.class);
    private final AgentToolCallExecutionRepository executions = mock(AgentToolCallExecutionRepository.class);
    private final AgentSessionRepository sessions = mock(AgentSessionRepository.class);
    private final AgentRunRecoveryPromptService service = new AgentRunRecoveryPromptService(
            runs, executions, sessions, new JacksonJsonCodec(new ObjectMapper().findAndRegisterModules()));

    @Test
    void attaches_durable_execution_record_to_a_manual_request_after_failure() {
        AgentRun failed = run(60L, 80L, "FAILED", "MODEL_TIMEOUT", "provider timed out");
        when(runs.listBySession(10L, 30L)).thenReturn(List.of(failed));
        when(sessions.listMessageRows(30L)).thenReturn(List.of(
                Map.of("turnId", 80L, "role", "user", "contentMarkdown", "Update the protagonist and chapter")
        ));
        when(executions.listByRun(60L)).thenReturn(List.of(
                execution(60L, "call-1", "story_bible_node_write", "SUCCEEDED",
                        "{\"status\":\"SUCCESS\",\"toolOutput\":\"{\\\"entityId\\\":\\\"123\\\",\\\"revision\\\":5}\"}"),
                execution(60L, "call-2", "quality_review", "FAILED",
                        "{\"status\":\"FAILED\",\"errorCode\":\"MODEL_TIMEOUT\",\"errorMessage\":\"provider timed out\"}")
        ));

        String result = service.attachToManualRequest(10L, 30L, "Continue with a tighter ending");

        assertThat(result)
                .contains(AgentRunRecoveryPromptService.RECOVERY_HEADER)
                .contains("Original request: Update the protagonist and chapter")
                .contains("tool=story_bible_node_write status=SUCCEEDED")
                .contains("entityId")
                .contains("tool=quality_review status=FAILED")
                .endsWith(AgentRunRecoveryPromptService.CURRENT_REQUEST_HEADER
                        + "\nContinue with a tighter ending");
    }

    @Test
    void leaves_manual_request_unchanged_after_a_successful_run() {
        when(runs.listBySession(10L, 30L)).thenReturn(List.of(
                run(60L, 80L, "FAILED", "MODEL_TIMEOUT", "provider timed out"),
                run(61L, 81L, "DONE", null, null)
        ));

        assertThat(service.attachToManualRequest(10L, 30L, "Start a new task"))
                .isEqualTo("Start a new task");
    }

    @Test
    void carries_all_consecutive_interrupted_attempts_but_not_earlier_successful_history() {
        AgentRun done = run(59L, 79L, "DONE", null, null);
        AgentRun failed = run(60L, 80L, "FAILED", "FIRST", "first failed");
        AgentRun cancelled = run(61L, 81L, "CANCELLED", "CANCELLED", "cancelled");
        when(runs.listBySession(10L, 30L)).thenReturn(List.of(done, failed, cancelled));
        when(sessions.listMessageRows(30L)).thenReturn(List.of(
                Map.of("turnId", 79L, "role", "user", "contentMarkdown", "successful request"),
                Map.of("turnId", 80L, "role", "user", "contentMarkdown", "failed request"),
                Map.of("turnId", 81L, "role", "user", "contentMarkdown", "cancelled request")
        ));

        String result = service.attachToManualRequest(10L, 30L, "Next message");

        assertThat(result).contains("runId=60", "runId=61", "failed request", "cancelled request")
                .doesNotContain("runId=59", "successful request");
    }

    private AgentRun run(Long runId, Long turnId, String status, String errorCode, String errorMessage) {
        return new AgentRun(runId, 10L, 30L, turnId, 50L, null,
                status, status.toLowerCase(), null, null, null, null, 0L, 1,
                null, errorCode, errorMessage, 0L, null, "trace", null, Instant.now());
    }

    private AgentToolCallExecution execution(Long runId, String callId, String toolCode,
                                             String status, String resultJson) {
        return new AgentToolCallExecution(100L + callId.hashCode(), runId, callId, toolCode,
                "a".repeat(64), 1L, status, resultJson,
                "FAILED".equals(status) ? "MODEL_TIMEOUT" : null,
                "FAILED".equals(status) ? "provider timed out" : null,
                Instant.now(), Instant.now());
    }
}
