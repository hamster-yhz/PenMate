package com.penmate.backend.domain.agent;

import com.penmate.backend.domain.agent.model.AgentSession;
import com.penmate.backend.domain.agent.model.AgentSessionRecoverySnapshot;
import com.penmate.backend.domain.agent.model.AgentTaskContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentSessionRecoveryPolicyTest {

    @Test
    void should_disallow_multiple_running_tasks_in_one_session() {
        AgentSession session = AgentSession.active(90001L, 101L, 201L, "测试会话");
        session.attachRunningTask(70001L);

        assertThatThrownBy(() -> session.attachRunningTask(70002L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("running task");
    }

    @Test
    void should_reject_null_running_task_id() {
        AgentSession session = AgentSession.active(90001L, 101L, 201L, "测试会话");

        assertThatThrownBy(() -> session.attachRunningTask(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskId");
    }

    @Test
    void should_expose_session_summary_fields_defined_by_recovery_contract() {
        AgentSession session = AgentSession.active(90001L, 101L, 201L, "测试会话");
        session.bindStyle(501L);
        session.markLastTaskStatus("WAITING_APPROVAL");

        assertThat(session.getSessionId()).isEqualTo(90001L);
        assertThat(session.getTitle()).isEqualTo("测试会话");
        assertThat(session.getStatus()).isEqualTo("ACTIVE");
        assertThat(session.getBoundStyle()).isEqualTo(501L);
        assertThat(session.getLastTaskStatus()).isEqualTo("WAITING_APPROVAL");
    }

    @Test
    void should_expose_recovery_snapshot_using_contract_shape_and_immutable_message_items() {
        AgentSession session = AgentSession.active(90001L, 101L, 201L, "测试会话");
        session.bindStyle(501L);
        session.markLastTaskStatus("WAITING_APPROVAL");
        AgentTaskContext activeTask = new AgentTaskContext();
        setField(activeTask, "selectedText", "原始选中文本");
        java.util.Map<String, Object> messageItem = java.util.Map.of(
                "messageId", 81001L,
                "role", "user",
                "content", "原始选中文本"
        );
        List<Object> messages = new ArrayList<>(List.of(messageItem));
        String pendingApproval = "approval-1";
        String workbenchContext = "{\"chapterId\":1}";
        AgentSessionRecoverySnapshot snapshot = AgentSessionRecoverySnapshot.of(
                session,
                activeTask,
                pendingApproval,
                messages,
                workbenchContext
        );

        assertThat(snapshot.getSession()).isNotSameAs(session);
        assertThat(snapshot.getSession().getSessionId()).isEqualTo(session.getSessionId());
        assertThat(snapshot.getSession().getTitle()).isEqualTo(session.getTitle());
        assertThat(snapshot.getSession().getBoundStyle()).isEqualTo(501L);
        assertThat(snapshot.getSession().getLastTaskStatus()).isEqualTo("WAITING_APPROVAL");
        assertThat(snapshot.getActiveTask()).isNotSameAs(activeTask);
        assertThat(snapshot.getActiveTask().getSelectedText()).isEqualTo("原始选中文本");
        assertThat(snapshot.getPendingApproval()).isEqualTo(pendingApproval);
        assertThat(snapshot.getMessages()).containsExactly(messageItem);
        assertThat(snapshot.getWorkbenchContext()).isEqualTo(workbenchContext);

        assertThatThrownBy(() -> snapshot.getMessages().add(java.util.Map.of("messageId", 81002L)))
                .isInstanceOf(UnsupportedOperationException.class);

        messages.add(java.util.Map.of("messageId", 81003L));
        assertThat(snapshot.getMessages()).containsExactly(messageItem);

        session.bindStyle(999L);
        session.markLastTaskStatus("RUNNING");
        setField(activeTask, "selectedText", "被污染后的文本");

        assertThat(snapshot.getSession().getBoundStyle()).isEqualTo(501L);
        assertThat(snapshot.getSession().getLastTaskStatus()).isEqualTo("WAITING_APPROVAL");
        assertThat(snapshot.getActiveTask().getSelectedText()).isEqualTo("原始选中文本");
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("failed to set field: " + fieldName, exception);
        }
    }
}
