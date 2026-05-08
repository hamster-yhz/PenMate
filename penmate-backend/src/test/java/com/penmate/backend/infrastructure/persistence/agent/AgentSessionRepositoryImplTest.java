package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.model.AgentSessionRecoverySnapshot;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSessionRepositoryImplTest {

    @Test
    void should_return_recovery_snapshot_when_session_exists() {
        AgentSessionMapper mapper = mock(AgentSessionMapper.class);
        BusinessIdGenerator businessIdGenerator = mock(BusinessIdGenerator.class);
        AgentSessionRepositoryImpl repository = new AgentSessionRepositoryImpl(mapper, businessIdGenerator);
        AgentSession session = AgentSession.active(90001L, 101L, 201L, "第三章夜雨追踪");
        session.bindStyle(81L);
        session.attachRunningTask(70001L);
        session.markLastTaskStatus("WAITING_APPROVAL");

        when(mapper.findSession(101L, 90001L)).thenReturn(session);

        AgentSessionRecoverySnapshot snapshot = repository.findRecoverySnapshot(101L, 90001L);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.getSession()).isNotNull();
        assertThat(snapshot.getSession().getSessionId()).isEqualTo(90001L);
        assertThat(snapshot.getSession().getBoundStyle()).isEqualTo(81L);
        assertThat(snapshot.getActiveTask()).isNotNull();
        assertThat(snapshot.getActiveTask().getTaskId()).isEqualTo(70001L);
        assertThat(snapshot.getActiveTask().getTaskStatus()).isEqualTo("WAITING_APPROVAL");
    }
}
