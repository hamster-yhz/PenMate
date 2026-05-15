package com.penmate.backend.application.agent.usecase;

import com.penmate.backend.application.agent.query.AgentSessionRecoveryQueryService;
import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.model.AgentSessionRecoverySnapshot;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 会话恢复应用服务测试。
 * <p>冻结 recovery DTO 对 pendingApproval 与 activeTask.taskStatus 的映射契约。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentSessionRecoveryAppServiceTest {

    @Mock
    private AgentSessionRecoveryQueryService agentSessionRecoveryQueryService;

    @InjectMocks
    private AgentSessionRecoveryAppService agentSessionRecoveryAppService;

    @Test
    void should_return_pending_approval_inside_recovery_snapshot() {
        AgentSession session = AgentSession.active(90001L, 101L, 201L, "第三章夜雨追踪");
        session.bindStyle(81L);
        session.markLastTaskStatus("WAITING_APPROVAL");

        AgentTaskContext activeTask = taskContext(70001L, "WAITING_APPROVAL");
        AgentSessionRecoverySnapshot recoverySnapshot = AgentSessionRecoverySnapshot.of(
                session,
                activeTask,
                "{\"approvalId\":88001,\"status\":\"pending\"}",
                List.of(),
                null
        );

        when(agentSessionRecoveryQueryService.getRecoverySnapshot(101L, 90001L, "trace-recovery-approval-1"))
                .thenReturn(recoverySnapshot);

        AgentSessionRecoveryResult snapshot = agentSessionRecoveryAppService.getRecovery(101L, 90001L, "trace-recovery-approval-1");

        assertThat(snapshot.pendingApproval())
                .as("pending approval should be present for recovery snapshot when task waits for approval")
                .isNotNull();
        assertThat(snapshot.session()).isNotNull();
        assertThat(snapshot.session().boundStyle()).isNotNull();
        assertThat(snapshot.session().boundStyle().styleId()).isEqualTo(81L);
        assertThat(snapshot.activeTask())
                .as("active task should exist in recovery snapshot")
                .isNotNull();
        assertThat(snapshot.activeTask().taskStatus())
                .as("recovery snapshot should expose canonical lowercase waiting_approval task status")
                .isEqualTo("waiting_approval");
    }

    private AgentTaskContext taskContext(Long taskId, String taskStatus) {
        AgentTaskContext context = new AgentTaskContext();
        setField(context, "taskId", taskId);
        setField(context, "taskStatus", taskStatus);
        setField(context, "modelSnapshotJson", "{\"model\":\"gpt\"}");
        return context;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to set field: " + fieldName, ex);
        }
    }
}
