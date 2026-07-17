package com.penmate.backend.application.agent.run;

import com.penmate.backend.application.common.exception.BusinessException;
import com.penmate.backend.domain.agent.run.model.AgentRunLease;
import com.penmate.backend.domain.agent.run.model.AgentRunStatus;
import com.penmate.backend.domain.agent.run.repository.AgentRunRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRunLeaseServiceTest {

    @Test
    void transient_failure_suspends_before_retry_limit() {
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentRunLease lease = lease(2);
        when(runs.ownsLease(eq(lease), any())).thenReturn(true);
        when(runs.transitionWithLease(eq(lease), eq(AgentRunStatus.SUSPENDED), eq("suspended"),
                eq(null), any(), eq("AGENT_RUN_TRANSIENT_FAILURE"), eq("timeout"))).thenReturn(true);

        AgentRunStatus result = new AgentRunLeaseService(runs)
                .handleFailure(lease, new IllegalStateException("timeout"));

        assertThat(result).isEqualTo(AgentRunStatus.SUSPENDED);
    }

    @Test
    void third_transient_failure_is_terminal() {
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentRunLease lease = lease(3);
        when(runs.ownsLease(eq(lease), any())).thenReturn(true);
        when(runs.transitionWithLease(eq(lease), eq(AgentRunStatus.FAILED), eq("failed"),
                eq(null), eq(null), eq("AGENT_RUN_TRANSIENT_FAILURE"), eq("timeout"))).thenReturn(true);

        assertThat(new AgentRunLeaseService(runs)
                .handleFailure(lease, new IllegalStateException("timeout")))
                .isEqualTo(AgentRunStatus.FAILED);
    }

    @Test
    void business_failure_never_retries() {
        AgentRunRepository runs = mock(AgentRunRepository.class);
        AgentRunLease lease = lease(1);
        when(runs.ownsLease(eq(lease), any())).thenReturn(true);
        when(runs.transitionWithLease(eq(lease), eq(AgentRunStatus.FAILED), eq("failed"),
                eq(null), eq(null), eq("BAD_REQUEST"), eq("invalid"))).thenReturn(true);

        assertThat(new AgentRunLeaseService(runs)
                .handleFailure(lease, BusinessException.badRequest("invalid")))
                .isEqualTo(AgentRunStatus.FAILED);
        verify(runs).transitionWithLease(eq(lease), eq(AgentRunStatus.FAILED), eq("failed"),
                eq(null), eq(null), eq("BAD_REQUEST"), eq("invalid"));
    }

    private AgentRunLease lease(int attempt) {
        return new AgentRunLease(70001L, "worker", 2L, attempt,
                AgentRunStatus.SUSPENDED, LocalDateTime.now().plusMinutes(1));
    }
}
