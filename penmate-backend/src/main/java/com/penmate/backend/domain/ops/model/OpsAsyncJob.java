package com.penmate.backend.domain.ops.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
/**
 * 异步运维任务实体。
 */
public class OpsAsyncJob {
    /** 异步任务主键 ID。 */
    private Long id;
    /** 任务类型。 */
    private String jobType;
    /** 业务键，用于关联业务对象。 */
    private String bizKey;
    /** 任务状态。 */
    private String status;
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

