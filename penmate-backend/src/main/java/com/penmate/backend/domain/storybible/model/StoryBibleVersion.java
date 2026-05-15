package com.penmate.backend.domain.storybible.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Story Bible 版本头。
 * <p>记录知识库的版本号与变更摘要，供审批、恢复与前端追踪。</p>
 */
@Data
public class StoryBibleVersion {

    /** 数据库物理主键 ID。 */
    private Long id;
    /** 版本业务 ID。 */
    private Long versionId;
    /** 所属 Story Bible 业务 ID。 */
    private Long storyBibleId;
    /** 所属项目业务 ID。 */
    private Long projectId;
    /** Story Bible 版本号。 */
    private Integer versionNo;
    /** 版本变更摘要。 */
    private String changeSummary;
    /** 创建版本的操作者业务 ID。 */
    private Long createdBy;
    /** 创建时间。 */
    private LocalDateTime createdAt;
}
