package com.penmate.backend.domain.agent.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
/**
 * 智能体生成任务实体。
 */
public class AgentGenerationTask {
    /** 任务主键 ID。 */
    private Long id;
    /** 所属项目 ID。 */
    private Long projectId;
    /** 关联会话 ID。 */
    private Long conversationId;
    /** 关联章节 ID。 */
    private Long chapterId;
    /**
     * 显式模型配置 ID。
     * <p>完全显式模式下由前端每次请求传入，不再走项目默认策略兜底。</p>
     */
    private Long modelConfigId;
    /** 任务类型（如续写、润色、总结）。 */
    private String taskType;
    /** 提示词快照文本。 */
    private String promptSnapshot;
    /** 风格配置快照（JSON/文本）。 */
    private String styleProfileSnapshot;
    /** 插件上下文快照（JSON）。 */
    private String pluginSnapshot;
    /** Token 消耗统计（JSON）。 */
    private String tokenUsageJson;
    /** 成本统计（JSON）。 */
    private String costJson;
    /** 全链路追踪 ID。 */
    private String traceId;
    /** 任务状态。 */
    private String status;
    /** 执行开始时间。 */
    private LocalDateTime startedAt;
    /** 执行完成时间。 */
    private LocalDateTime finishedAt;
    /** 失败原因。 */
    private String errorMsg;
    /** 创建时间。 */
    private LocalDateTime createdAt;

}

