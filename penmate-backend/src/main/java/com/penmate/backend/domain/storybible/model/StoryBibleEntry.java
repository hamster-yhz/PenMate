package com.penmate.backend.domain.storybible.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Story Bible 结构化条目。
 * <p>用于承载项目级长期知识，不等于运行时 prompt 大文本。</p>
 */
@Data
public class StoryBibleEntry {

    /** 数据库物理主键 ID。 */
    private Long id;
    /** 条目业务 ID。 */
    private Long entryId;
    /** 所属 Story Bible 业务 ID。 */
    private Long storyBibleId;
    /** 所属项目业务 ID。 */
    private Long projectId;
    /** 条目类型：character/world/plot/item/faction/rule。 */
    private String entryType;
    /** 条目稳定键；供上下文聚合和去重。 */
    private String entryKey;
    /** 条目标题。 */
    private String title;
    /** 条目正文。 */
    private String content;
    /** 规范状态：CANON/PROPOSED/ASSUMPTION。 */
    private String canonicalStatus;
    /** 风险等级：1低/2中/3高。 */
    private Integer riskLevel;
    /** 来源引用集合。 */
    private List<StoryBibleSourceRef> sourceRefs;
    /** 起始生效章节 business id；NULL 表示项目开头即生效。 */
    private Long validFromChapterId;
    /** 截止生效章节 business id；NULL 表示持续生效。 */
    private Long validToChapterId;
    /** 归属 Story Bible 版本号。 */
    private Integer versionNo;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除时间。 */
    private LocalDateTime deletedAt;
}
