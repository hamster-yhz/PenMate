package com.penmate.backend.domain.agent.model;

/**
 * 智能体任务结果快照。
 */
public class AgentTaskResult {

    /** 结果业务 ID。 */
    private Long resultId;
    /** 所属任务业务 ID。 */
    private Long taskId;
    /** 结果状态。 */
    private String resultStatus;
    /** 关联主助手消息业务 ID。 */
    private Long assistantMessageId;
    /** Markdown 输出快照。 */
    private String outputMarkdown;
    /** 结构化输出 JSON。 */
    private String outputStructuredJson;
    /** 工具执行轨迹 JSON。 */
    private String toolTraceJson;
    /** 草稿结果摘要 JSON。 */
    private String draftSummary;
    /** 质量审查摘要 JSON。 */
    private String qualityReportSummary;
    /** Todo 规划摘要 JSON。 */
    private String todoSummary;
    /** Story Bible 提案摘要 JSON。 */
    private String storyBibleProposalSummary;
    /** Token 用量统计 JSON。 */
    private String tokenUsageJson;
    /** 成本用量统计 JSON。 */
    private String costUsageJson;
    /** 错误码。 */
    private String errorCode;
    /** 错误消息。 */
    private String errorMessage;

    public void setResultId(Long resultId) {
        this.resultId = resultId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public void setResultStatus(String resultStatus) {
        this.resultStatus = resultStatus;
    }

    public void setAssistantMessageId(Long assistantMessageId) {
        this.assistantMessageId = assistantMessageId;
    }

    public void setOutputMarkdown(String outputMarkdown) {
        this.outputMarkdown = outputMarkdown;
    }

    public void setOutputStructuredJson(String outputStructuredJson) {
        this.outputStructuredJson = outputStructuredJson;
    }

    public void setToolTraceJson(String toolTraceJson) {
        this.toolTraceJson = toolTraceJson;
    }

    public void setDraftSummary(String draftSummary) {
        this.draftSummary = draftSummary;
    }

    public void setQualityReportSummary(String qualityReportSummary) {
        this.qualityReportSummary = qualityReportSummary;
    }

    public void setTodoSummary(String todoSummary) {
        this.todoSummary = todoSummary;
    }

    public void setStoryBibleProposalSummary(String storyBibleProposalSummary) {
        this.storyBibleProposalSummary = storyBibleProposalSummary;
    }

    public void setTokenUsageJson(String tokenUsageJson) {
        this.tokenUsageJson = tokenUsageJson;
    }

    public void setCostUsageJson(String costUsageJson) {
        this.costUsageJson = costUsageJson;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getResultId() {
        return resultId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getResultStatus() {
        return resultStatus;
    }

    public Long getAssistantMessageId() {
        return assistantMessageId;
    }

    public String getOutputMarkdown() {
        return outputMarkdown;
    }

    public String getOutputStructuredJson() {
        return outputStructuredJson;
    }

    public String getToolTraceJson() {
        return toolTraceJson;
    }

    public String getDraftSummary() {
        return draftSummary;
    }

    public String getQualityReportSummary() {
        return qualityReportSummary;
    }

    public String getTodoSummary() {
        return todoSummary;
    }

    public String getStoryBibleProposalSummary() {
        return storyBibleProposalSummary;
    }

    public String getTokenUsageJson() {
        return tokenUsageJson;
    }

    public String getCostUsageJson() {
        return costUsageJson;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
