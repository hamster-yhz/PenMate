package com.penmate.backend.application.agent.query;

import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.model.AgentSessionRecoverySnapshot;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import com.penmate.backend.domain.agent.model.PendingToolInvocationSnapshot;
import com.penmate.backend.domain.agent.repository.AgentSessionRepository;
import com.penmate.backend.domain.agent.repository.PendingToolInvocationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentSessionRecoveryQueryServiceTest {

    @Mock
    private AgentSessionRepository agentSessionRepository;

    @Mock
    private PendingToolInvocationRepository pendingToolInvocationRepository;

    @InjectMocks
    private AgentSessionRecoveryQueryService agentSessionRecoveryQueryService;

    @Test
    void should_restore_pending_approval_when_approval_id_differs_from_task_id() {
        AgentSession session = AgentSession.active(90001L, 101L, 201L, "第三章夜雨追踪");
        session.bindStyle(81L);
        session.markLastTaskStatus("WAITING_APPROVAL");

        AgentTaskContext activeTask = AgentTaskContext.recoveryOf(70001L, "WAITING_APPROVAL", 88001L);
        setField(activeTask, "selectedText", "夜雨中的追踪在巷口停住。 ");
        setField(activeTask, "contextId", 61001L);
        setField(activeTask, "chapterId", 301L);
        setField(activeTask, "modelSnapshotJson", "{\"model\":\"gpt\"}");

        AgentSessionRecoverySnapshot storedSnapshot = AgentSessionRecoverySnapshot.of(
                session,
                activeTask,
                null,
                List.of(),
                null
        );

        PendingToolInvocationSnapshot pendingSnapshot = new PendingToolInvocationSnapshot(
                88001L,
                101L,
                70001L,
                90001L,
                "book.update",
                "{}",
                "{}",
                201L,
                "trace-recovery-approval-1",
                "idem-1",
                "pending",
                "loop-1",
                1,
                "tool-call-1",
                "[]",
                "[]",
                "RESUME_LOOP",
                "{\"approvalId\":88001}"
        );

        when(agentSessionRepository.findRecoverySnapshot(101L, 90001L)).thenReturn(storedSnapshot);
        when(pendingToolInvocationRepository.findByApprovalId(88001L)).thenReturn(pendingSnapshot);

        AgentSessionRecoverySnapshot snapshot = agentSessionRecoveryQueryService.getRecoverySnapshot(101L, 90001L, "trace-recovery-approval-1");

        assertThat(snapshot.getSession()).isNotNull();
        assertThat(snapshot.getSession().getBoundStyle()).isEqualTo(81L);
        assertThat(snapshot.getPendingApproval()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) snapshot.getPendingApproval()).get("approvalId")).isEqualTo(88001L);
        assertThat(((Map<?, ?>) snapshot.getPendingApproval()).get("taskId")).isEqualTo(70001L);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("failed to set field: " + fieldName, ex);
        }
    }
}
