package com.penmate.backend.domain.storybible.model;

import lombok.Data;

import java.time.Instant;

/**
 * Story Bible 聚合根。
 * <p>表示项目级长期知识库入口，供单 Main Orchestrator 在上下文构建阶段读取。</p>
 */
@Data
public class StoryBible {

    /** 数据库物理主键 ID。 */
    private Long id;
    /** Story Bible 业务 ID。 */
    private Long storyBibleId;
    /** 所属项目业务 ID。 */
    private Long projectId;
    /** 知识库标题。 */
    private String title;
    /** 知识库描述。 */
    private String description;
    /** 当前激活版本号。 */
    private Long contentRevision;
    /** 创建时间。 */
    private Instant createdAt;
    /** 更新时间。 */
    private Instant updatedAt;
    /** 逻辑删除时间。 */
    private Instant deletedAt;
}
