package com.penmate.backend.domain.todo.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话级 Todo 实体。
 */
@Data
public class SessionTodo {

    /** 数据库物理主键 ID。 */
    private Long id;
    /** Todo 业务 ID。 */
    private Long todoId;
    /** 所属项目业务 ID。 */
    private Long projectId;
    /** 所属会话业务 ID。 */
    private Long sessionId;
    /** 来源任务业务 ID。 */
    private Long taskId;
    /** 待办标题。 */
    private String title;
    /** 待办说明。 */
    private String description;
    /** 来源类型。 */
    private String sourceType;
    /** 待办状态。 */
    private String todoStatus;
    private String status;
    private Integer priority;
    private Integer orderIndex;
    private List<String> dependencies = new ArrayList<>();
    private String summary;
    private String blockedReason;
    private String errorSummary;
    private String metadata;
    /** 完成时间。 */
    private LocalDateTime completedAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除时间。 */
    private LocalDateTime deletedAt;
}
