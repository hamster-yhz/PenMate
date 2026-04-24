package com.penmate.backend.domain.novel.model;

import lombok.Data;
import java.time.LocalDateTime;

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
    /** 关联大纲节点业务 ID。 */
    private Long outlineNodeId;
    /** 章节标题。 */
    private String title;
    /** 章节序号。 */
    private Integer chapterNo;
    /** 章节状态。 */
    private Integer status;
    /** 字数统计。 */
    private Integer wordCount;
    /** 内容摘要。 */
    private String excerpt;
    /** 正文对象存储键。 */
    private String contentObjectKey;
    /** 正文对象 ETag。 */
    private String contentEtag;
    /** 正文内容大小（字节）。 */
    private Long contentSize;
    /** 正文内容校验和。 */
    private String contentChecksum;
    /** 存储服务提供商。 */
    private String storageProvider;
    /** 最近一次 AI 生成时间。 */
    private LocalDateTime lastGeneratedAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除时间。 */
    private LocalDateTime deletedAt;

}

