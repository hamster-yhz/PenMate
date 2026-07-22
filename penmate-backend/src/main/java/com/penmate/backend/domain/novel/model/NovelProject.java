package com.penmate.backend.domain.novel.model;

import lombok.Data;
import java.time.Instant;
import java.util.List;

@Data
/**
 * 小说项目实体。
 */
public class NovelProject {
    /** 数据库物理主键 ID。 */
    private Long id;
    /** 项目业务 ID。 */
    private Long projectId;
    /** 项目拥有者用户业务 ID。 */
    private Long ownerUserId;
    /** 小说项目标题。 */
    private String title;
    /** 项目简介。 */
    private String summary;
    /** 作品主类型。 */
    private String genre;
    /** “其他”类型对应的自定义名称。 */
    private String customGenre;
    /** 作品级整理标签。 */
    private List<String> tags;
    private String coverOriginalObjectKey;
    private String coverDisplayObjectKey;
    private String coverThumbnailObjectKey;
    private Long coverPendingUploadId;
    /** API 查询投影，不参与项目写入。 */
    private String coverUrl;
    /** 书架缩略图查询投影，不参与项目写入。 */
    private String coverThumbnailUrl;
    /** 书架查询投影，不参与项目写入。 */
    private Long totalWords;
    /** 书架查询投影，不参与项目写入。 */
    private Integer totalChapters;
    /** 项目状态。 */
    private Integer status;
    private Long structureRevision;
    /** 创建时间。 */
    private Instant createdAt;
    /** 更新时间。 */
    private Instant updatedAt;
    /** 逻辑删除时间。 */
    private Instant deletedAt;

}

