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
    /** Token 用量统计 JSON。 */
    private String tokenUsageJson;
    /** 成本用量统计 JSON。 */
    private String costUsageJson;
    /** 错误码。 */
    private String errorCode;
    /** 错误消息。 */
    private String errorMessage;

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
