package com.penmate.backend.application.agent.run;

import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.agent.run.model.AgentRunPendingApproval;
import com.penmate.backend.domain.agent.run.repository.AgentRunPendingApprovalRepository;
import com.penmate.backend.domain.agent.run.repository.AgentRunProjectionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRunRecoveryAppServiceTest {

    @Test
    void restores_the_pending_story_bible_approval_and_affected_node() {
        AgentSessionRepository sessions = mock(AgentSessionRepository.class);
        AgentRunProjectionRepository projections = mock(AgentRunProjectionRepository.class);
        AgentRunPendingApprovalRepository approvals = mock(AgentRunPendingApprovalRepository.class);
        when(sessions.findSession(10L, 20L)).thenReturn(AgentSession.active(20L, 10L, 30L, "Draft"));
        when(sessions.listMessageRows(20L)).thenReturn(List.of());
        when(projections.findLatestRunForSession(10L, 20L)).thenReturn(Map.of(
                "turnId", 40L, "runId", 50L, "runStatus", "WAITING_APPROVAL",
                "runPhase", "waiting_approval", "latestSequence", 8L));
        when(approvals.findPendingByRunId(50L)).thenReturn(new AgentRunPendingApproval(
                1L, 60L, 60L, 50L, 10L, 20L, 40L, "call-1", "story_bible_update",
                "{\"operation\":\"batch\",\"operations\":[{\"kind\":\"update_node\",\"nodeId\":71}]}",
                "{}", "[]", "50:call-1", "PENDING", 30L, "trace", null, null));

        AgentRunRecoveryResult result = new AgentRunRecoveryAppService(sessions, projections, approvals)
                .getRecovery(10L, 20L, "trace");

        assertThat(result.pendingApproval()).isInstanceOfSatisfying(Map.class, pending ->
                assertThat(((Map<?, ?>) pending.get("approvalPreview")).get("nodeId")).isEqualTo("71"));
        assertThat(result.messages()).singleElement().isInstanceOfSatisfying(Map.class, message -> {
            assertThat(message).containsEntry("approvalId", "60")
                    .containsEntry("toolCode", "story_bible_update");
            assertThat(((Map<?, ?>) message.get("approvalPreview")).get("nodeId")).isEqualTo("71");
        });
    }
}
