package com.penmate.backend.domain.agent.model;

import java.util.List;

/**
 * 会话恢复唯一事实源快照。
 */
public class AgentSessionRecoverySnapshot {

    /** 会话摘要。 */
    private AgentSession session;
    /** 当前激活任务上下文摘要。 */
    private AgentTaskContext activeTask;
    /** 待处理审批快照。 */
    private Object pendingApproval;
    /** 最近消息列表；元素语义与 recovery contract 对齐，不再借用 turn 摘要伪装消息。 */
    private List<Object> messages;
    /** 工作台上下文快照。 */
    private String workbenchContext;

    private AgentSessionRecoverySnapshot(AgentSession session,
                                         AgentTaskContext activeTask,
                                         Object pendingApproval,
                                         List<?> messages,
                                         String workbenchContext) {
        this.session = session;
        this.activeTask = activeTask;
        this.pendingApproval = pendingApproval;
        this.messages = messages == null ? List.of() : List.copyOf(messages);
        this.workbenchContext = workbenchContext;
    }

    public static AgentSessionRecoverySnapshot of(AgentSession session,
                                                  AgentTaskContext activeTask,
                                                  Object pendingApproval,
                                                  List<?> messages,
                                                  String workbenchContext) {
        return new AgentSessionRecoverySnapshot(
                AgentSession.summaryOf(session),
                AgentTaskContext.snapshotOf(activeTask),
                pendingApproval,
                messages,
                workbenchContext
        );
    }

    public AgentSession getSession() {
        return session;
    }

    public AgentTaskContext getActiveTask() {
        return activeTask;
    }

    public Object getPendingApproval() {
        return pendingApproval;
    }

    public List<Object> getMessages() {
        return messages;
    }

    public String getWorkbenchContext() {
        return workbenchContext;
    }
}
