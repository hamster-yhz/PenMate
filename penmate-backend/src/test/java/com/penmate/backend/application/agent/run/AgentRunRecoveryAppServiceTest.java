package com.penmate.backend.application.agent.run;

import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunProjectionRepository;
import com.penmate.backend.application.agent.tool.runtime.ToolApprovalPreview;
import com.penmate.backend.application.agent.skill.AgentSkillActivationService;
import com.penmate.backend.infrastructure.serialization.JacksonJsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRunRecoveryAppServiceTest {

    @Test
    void restores_a_completed_run_when_the_projection_has_no_phase() {
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        AgentRunProjectionRepository projections = mock(AgentRunProjectionRepository.class);
        AgentRunPendingApprovalRepository approvals = mock(AgentRunPendingApprovalRepository.class);
        AgentPartialMessageCheckpointStore partialMessages = mock(AgentPartialMessageCheckpointStore.class);
        when(sessions.findSession(10L, 20L)).thenReturn(AgentSession.active(20L, 10L, 30L, "Draft"));
        when(sessions.listMessageRows(20L)).thenReturn(List.of());
        Map<String, Object> projection = new java.util.LinkedHashMap<>();
        projection.put("turnId", 40L);
        projection.put("runId", 50L);
        projection.put("runStatus", "DONE");
        projection.put("runPhase", null);
        projection.put("latestSequence", 8L);
        when(projections.findLatestRunForSession(10L, 20L)).thenReturn(projection);

        AgentRunRecoveryResult result = new AgentRunRecoveryAppService(
                sessions, projections, approvals, partialMessages, preview(), mock(AgentSkillActivationService.class))
                .getRecovery(10L, 20L, "trace");

        assertThat(result.activeRun()).isNotNull();
        assertThat(result.workbenchContext()).isInstanceOf(Map.class);
        Map<?, ?> context = (Map<?, ?>) result.workbenchContext();
        assertThat(context.containsKey("activeRun")).isTrue();
        Map<?, ?> activeRun = (Map<?, ?>) context.get("activeRun");
        assertThat(activeRun.get("runStatus")).isEqualTo("DONE");
        assertThat(activeRun.containsKey("runPhase")).isTrue();
        assertThat(activeRun.get("runPhase")).isNull();
    }

    @Test
    void restores_the_pending_story_bible_approval_and_affected_node() {
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        AgentRunProjectionRepository projections = mock(AgentRunProjectionRepository.class);
        AgentRunPendingApprovalRepository approvals = mock(AgentRunPendingApprovalRepository.class);
        AgentPartialMessageCheckpointStore partialMessages = mock(AgentPartialMessageCheckpointStore.class);
        when(sessions.findSession(10L, 20L)).thenReturn(AgentSession.active(20L, 10L, 30L, "Draft"));
        when(sessions.listMessageRows(20L)).thenReturn(List.of());
        when(projections.findLatestRunForSession(10L, 20L)).thenReturn(Map.of(
                "turnId", 40L, "runId", 50L, "runStatus", "WAITING_APPROVAL",
                "runPhase", "waiting_approval", "latestSequence", 8L));
        when(approvals.findPendingByRunId(50L)).thenReturn(new AgentRunPendingApproval(
                1L, 60L, 60L, 50L, 10L, 20L, 40L, "call-1", "story_bible_node_write",
                "{\"operation\":\"update\",\"nodeId\":71,\"expectedRevision\":3}",
                "{}", "[]", "50:call-1", "PENDING", 30L, "trace", null, null));

        AgentRunRecoveryResult result = new AgentRunRecoveryAppService(
                sessions, projections, approvals, partialMessages, preview(), mock(AgentSkillActivationService.class))
                .getRecovery(10L, 20L, "trace");

        assertThat(result.pendingApproval()).isInstanceOfSatisfying(Map.class, pending ->
                assertThat(((Map<?, ?>) pending.get("approvalPreview")).get("nodeId")).isEqualTo("71"));
        assertThat(result.messages()).singleElement().isInstanceOfSatisfying(Map.class, message -> {
            assertThat(message).containsEntry("approvalId", "60")
                    .containsEntry("toolCode", "story_bible_node_write");
            assertThat(((Map<?, ?>) message.get("approvalPreview")).get("nodeId")).isEqualTo("71");
        });
    }

    @Test
    void restores_partial_assistant_text_for_an_active_run() {
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        AgentRunProjectionRepository projections = mock(AgentRunProjectionRepository.class);
        AgentRunPendingApprovalRepository approvals = mock(AgentRunPendingApprovalRepository.class);
        AgentPartialMessageCheckpointStore partialMessages = mock(AgentPartialMessageCheckpointStore.class);
        when(sessions.findSession(10L, 20L)).thenReturn(AgentSession.active(20L, 10L, 30L, "Draft"));
        when(sessions.listMessageRows(20L)).thenReturn(List.of());
        when(projections.findLatestRunForSession(10L, 20L)).thenReturn(Map.of(
                "turnId", 40L, "runId", 50L, "runStatus", "RUNNING",
                "runPhase", "executing", "latestSequence", 8L));
        when(partialMessages.find(50L)).thenReturn(Optional.of(
                new AgentPartialMessageCheckpointStore.Snapshot(
                        50L, 40L, "已经生成的部分内容", 9L, Instant.parse("2026-07-21T08:00:00Z"))));

        AgentRunRecoveryResult result = new AgentRunRecoveryAppService(
                sessions, projections, approvals, partialMessages, preview(), mock(AgentSkillActivationService.class)).getRecovery(10L, 20L, "trace");

        assertThat(result.messages()).singleElement().isInstanceOfSatisfying(Map.class, message -> {
            assertThat(message).containsEntry("messageId", "partial-50")
                    .containsEntry("turnId", "40")
                    .containsEntry("runId", "50")
                    .containsEntry("role", "assistant")
                    .containsEntry("contentMarkdown", "已经生成的部分内容")
                    .containsEntry("partial", true);
        });
    }

    @Test
    void does_not_restore_an_ambiguous_previous_attempt_answer_into_the_active_run() {
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        AgentRunProjectionRepository projections = mock(AgentRunProjectionRepository.class);
        AgentRunPendingApprovalRepository approvals = mock(AgentRunPendingApprovalRepository.class);
        AgentPartialMessageCheckpointStore partialMessages = mock(AgentPartialMessageCheckpointStore.class);
        when(sessions.findSession(10L, 20L)).thenReturn(AgentSession.active(20L, 10L, 30L, "Draft"));
        when(sessions.listMessageRows(20L)).thenReturn(List.of(new java.util.LinkedHashMap<>(Map.of(
                "messageId", "old-assistant", "turnId", "40", "role", "assistant",
                "contentMarkdown", "previous attempt answer"))));
        when(projections.findLatestRunForSession(10L, 20L)).thenReturn(Map.of(
                "turnId", 40L, "runId", 51L, "runStatus", "RUNNING",
                "runPhase", "routing", "latestSequence", 2L));
        when(partialMessages.find(51L)).thenReturn(Optional.empty());

        AgentRunRecoveryResult result = new AgentRunRecoveryAppService(
                sessions, projections, approvals, partialMessages, preview(), mock(AgentSkillActivationService.class)).getRecovery(10L, 20L, "trace");

        assertThat(result.messages()).isEmpty();
    }

    private ToolApprovalPreview preview() {
        return new ToolApprovalPreview(
                new JacksonJsonCodec(new ObjectMapper()),
                List.of(new com.penmate.backend.application.agent.tool.runtime.StoryBibleV2ApprovalPreviewConfiguration()
                        .storyBibleNodeWriteApprovalPreview()));
    }
}
