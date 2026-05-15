package com.penmate.backend.domain.agent.model;

/**
 * 智能体任务请求上下文快照。
 */
public class AgentTaskContext {

    /** 上下文业务 ID。 */
    private Long contextId;
    /** 所属 turn 业务 ID。 */
    private Long turnId;
    /** 所属任务业务 ID。 */
    private Long taskId;
    /** 任务状态；recovery contract 的 activeTask.taskStatus 来源。 */
    private String taskStatus;
    /** 当前挂起审批单业务 ID；WAITING_APPROVAL 恢复时作为唯一断点指针。 */
    private Long activeApprovalId;
    /** 关联章节业务 ID。 */
    private Long chapterId;
    /** 选中文本快照。 */
    private String selectedText;
    /** 大纲快照 JSON。 */
    private String outlineSnapshotJson;
    /** 卡片快照 JSON。 */
    private String cardsSnapshotJson;
    /** RAG 检索快照 JSON。 */
    private String ragSnapshotJson;
    /** 插件绑定快照 JSON。 */
    private String pluginBindingsJson;
    /** 风格快照 JSON。 */
    private String styleSnapshotJson;
    /** 模型快照 JSON。 */
    private String modelSnapshotJson;
    /** TaskProfile 快照 JSON。 */
    private String taskProfileJson;
    /** PromptPlan 快照 JSON。 */
    private String promptPlanJson;
    /** ContextPackage 快照 JSON。 */
    private String contextPackageJson;
    /** 当前运行中的工具调用快照 JSON。 */
    private String activeToolCallsSnapshot;
    /** 最近一次运行态状态。 */
    private String lastRuntimeStatus;
    /** 恢复游标；用于标记从哪个运行态断点恢复。 */
    private String recoveryCursor;
    /** 上下文哈希，用于恢复一致性校验。 */
    private String contextHash;

    public static AgentTaskContext snapshotOf(AgentTaskContext source) {
        if (source == null) {
            return null;
        }
        AgentTaskContext copied = new AgentTaskContext();
        copied.contextId = source.contextId;
        copied.turnId = source.turnId;
        copied.taskId = source.taskId;
        copied.taskStatus = source.taskStatus;
        copied.activeApprovalId = source.activeApprovalId;
        copied.chapterId = source.chapterId;
        copied.selectedText = source.selectedText;
        copied.outlineSnapshotJson = source.outlineSnapshotJson;
        copied.cardsSnapshotJson = source.cardsSnapshotJson;
        copied.ragSnapshotJson = source.ragSnapshotJson;
        copied.pluginBindingsJson = source.pluginBindingsJson;
        copied.styleSnapshotJson = source.styleSnapshotJson;
        copied.modelSnapshotJson = source.modelSnapshotJson;
        copied.taskProfileJson = source.taskProfileJson;
        copied.promptPlanJson = source.promptPlanJson;
        copied.contextPackageJson = source.contextPackageJson;
        copied.activeToolCallsSnapshot = source.activeToolCallsSnapshot;
        copied.lastRuntimeStatus = source.lastRuntimeStatus;
        copied.recoveryCursor = source.recoveryCursor;
        copied.contextHash = source.contextHash;
        return copied;
    }

    public static AgentTaskContext recoveryOf(Long taskId, String taskStatus, Long activeApprovalId) {
        AgentTaskContext context = new AgentTaskContext();
        context.taskId = taskId;
        context.taskStatus = taskStatus;
        context.activeApprovalId = activeApprovalId;
        return context;
    }

    public static AgentTaskContext runningOf(Long contextId,
                                             Long taskId,
                                             String taskStatus,
                                             Long chapterId,
                                             String selectedText) {
        AgentTaskContext context = new AgentTaskContext();
        context.contextId = contextId;
        context.taskId = taskId;
        context.taskStatus = taskStatus;
        context.chapterId = chapterId;
        context.selectedText = selectedText;
        return context;
    }

    public Long getContextId() {
        return contextId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public Long getTurnId() {
        return turnId;
    }

    public String getTaskStatus() {
        return taskStatus;
    }

    public Long getActiveApprovalId() {
        return activeApprovalId;
    }

    public Long getChapterId() {
        return chapterId;
    }

    public String getSelectedText() {
        return selectedText;
    }

    public String getOutlineSnapshotJson() {
        return outlineSnapshotJson;
    }

    public String getCardsSnapshotJson() {
        return cardsSnapshotJson;
    }

    public String getRagSnapshotJson() {
        return ragSnapshotJson;
    }

    public String getPluginBindingsJson() {
        return pluginBindingsJson;
    }

    public String getStyleSnapshotJson() {
        return styleSnapshotJson;
    }

    public String getModelSnapshotJson() {
        return modelSnapshotJson;
    }

    public String getTaskProfileJson() {
        return taskProfileJson;
    }

    public String getPromptPlanJson() {
        return promptPlanJson;
    }

    public String getContextPackageJson() {
        return contextPackageJson;
    }

    public String getActiveToolCallsSnapshot() {
        return activeToolCallsSnapshot;
    }

    public String getLastRuntimeStatus() {
        return lastRuntimeStatus;
    }

    public String getRecoveryCursor() {
        return recoveryCursor;
    }

    public String getContextHash() {
        return contextHash;
    }

    /**
     * 为当前任务上下文写入风格快照。
     * <p>Task 7 起 prompt 装配只消费 task context 中的风格绑定快照，不再依赖旧字段兼容。</p>
     */
    public void setStyleSnapshotJson(String styleSnapshotJson) {
        this.styleSnapshotJson = styleSnapshotJson;
    }

    public void setTaskProfileJson(String taskProfileJson) {
        this.taskProfileJson = taskProfileJson;
    }

    public void setPromptPlanJson(String promptPlanJson) {
        this.promptPlanJson = promptPlanJson;
    }

    public void setContextPackageJson(String contextPackageJson) {
        this.contextPackageJson = contextPackageJson;
    }

    public void setActiveToolCallsSnapshot(String activeToolCallsSnapshot) {
        this.activeToolCallsSnapshot = activeToolCallsSnapshot;
    }

    public void setLastRuntimeStatus(String lastRuntimeStatus) {
        this.lastRuntimeStatus = lastRuntimeStatus;
    }

    public void setRecoveryCursor(String recoveryCursor) {
        this.recoveryCursor = recoveryCursor;
    }

    public void setContextId(Long contextId) {
        this.contextId = contextId;
    }

    public void setTurnId(Long turnId) {
        this.turnId = turnId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }

    public void setActiveApprovalId(Long activeApprovalId) {
        this.activeApprovalId = activeApprovalId;
    }
}
