package com.penmate.backend.domain.novel.model;

import lombok.Data;
import java.time.Instant;

@Data
/**
 * 小说章节实体。
 */
public class NovelChapter {
    /** 数据库物理主键 ID。 */
    private Long id;
    /** 章节业务 ID。 */
    private Long chapterId;
    /** 所属项目业务 ID。 */
    private Long projectId;
    /** 所属卷册业务 ID。 */
    private Long volumeId;
    /** 章节标题。 */
    private String title;
    /** 章节序号。 */
    private Integer sortOrder;
    private Integer displayNo;
    /** 字数统计。 */
    private Integer wordCount;
    /** 当前章节正文，PostgreSQL 是唯一正式数据源。 */
    private String content;
    /** 正文并发修订号，不代表可浏览的历史版本。 */
    private Long contentRevision;
    private String leaseOwnerType;
    private Long leaseOwnerId;
    private String leaseToken;
    private Instant leaseExpiresAt;
    /** 创建时间。 */
    private Instant createdAt;
    /** 更新时间。 */
    private Instant updatedAt;
    /** 逻辑删除时间。 */
    private Instant deletedAt;

}

