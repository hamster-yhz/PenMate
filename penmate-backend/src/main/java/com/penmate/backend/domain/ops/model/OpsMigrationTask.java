package com.penmate.backend.domain.ops.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
/**
 * 运维迁移任务实体。
 */
public class OpsMigrationTask {
    /** 数据库物理主键 ID。 */
    private Long id;
    /** 迁移任务业务 ID。 */
    private Long migrationId;
    /** 迁移任务类型。 */
    private String migrationType;
    /** 迁移任务状态。 */
    private String status;
    /** 当前进度百分比。 */
    private Integer progressPct;
    /** 迁移结果摘要（JSON）。 */
    private String summaryJson;
    /** 失败错误信息。 */
    private String errorMsg;
    /** 开始执行时间。 */
    private LocalDateTime startedAt;
    /** 结束执行时间。 */
    private LocalDateTime finishedAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;

}

