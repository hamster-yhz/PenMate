package com.penmate.backend.infrastructure.persistence.agent;

import com.penmate.backend.domain.agent.model.AgentSessionRecoverySnapshot;
import com.penmate.backend.domain.shared.service.BusinessIdGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSessionRepositoryImplTest {

    @Test
    void should_define_session_lock_mapper_method_for_turn_sequence_allocation() {
        assertThatCode(() -> AgentSessionMapper.class.getMethod(
                "lockSessionForTurnAppend",
                Long.class,
                Long.class
        )).doesNotThrowAnyException();
    }

    @Test
    void should_call_session_lock_before_reading_max_turn_sequence() throws Exception {
        AgentSessionMapper mapper = mock(AgentSessionMapper.class);
        BusinessIdGenerator businessIdGenerator = mock(BusinessIdGenerator.class);
        AgentSessionRepositoryImpl repository = new AgentSessionRepositoryImpl(mapper, businessIdGenerator);
        when(mapper.maxTurnSeq(90001L)).thenReturn(3);

        repository.nextTurnSeq(90001L);

        inOrder(mapper).verify(mapper).lockSessionForTurnAppend(null, 90001L);
        inOrder(mapper).verify(mapper).maxTurnSeq(90001L);
    }

    @Test
    void should_return_recovery_snapshot_when_session_exists() {
        AgentSessionMapper mapper = mock(AgentSessionMapper.class);
        BusinessIdGenerator businessIdGenerator = mock(BusinessIdGenerator.class);
        AgentSessionRepositoryImpl repository = new AgentSessionRepositoryImpl(mapper, businessIdGenerator);
        when(mapper.findSessionRow(101L, 90001L)).thenReturn(Map.of(
                "id", 1L,
                "sessionId", 90001L,
                "projectId", 101L,
                "ownerUserId", 201L,
                "title", "第三章夜雨追踪",
                "sessionStatus", "ACTIVE",
                "boundStyleId", 81L,
                "activeContextVersion", 1,
                "lastTurnId", 60001L,
                "lastTaskId", 70001L
        ));
        when(mapper.findTaskRow(90001L, 70001L)).thenReturn(Map.of(
                "taskId", 70001L,
                "taskStatus", "WAITING_APPROVAL"
        ));
        when(mapper.findTaskContextRow(70001L)).thenReturn(null);
        when(mapper.listMessageRows(90001L)).thenReturn(List.of());

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
