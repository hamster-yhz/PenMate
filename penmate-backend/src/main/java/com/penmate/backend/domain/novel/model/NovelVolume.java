package com.penmate.backend.domain.novel.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
/**
 * 小说卷册实体。
 */
public class NovelVolume {
    /** 卷册主键 ID。 */
    private Long id;
    /** 所属项目 ID。 */
    private Long projectId;
    /** 卷册标题。 */
    private String title;
    /** 在项目内的排序序号。 */
    private Integer sortOrder;
    /** 卷册描述。 */
    private String description;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除时间。 */
    private LocalDateTime deletedAt;

}

