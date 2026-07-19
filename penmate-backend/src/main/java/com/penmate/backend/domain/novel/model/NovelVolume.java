package com.penmate.backend.domain.novel.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.time.Instant;

@Data
/**
 * 小说卷册实体。
 */
public class NovelVolume {
    /** 数据库物理主键 ID。 */
    private Long id;
    /** 卷册业务 ID。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long volumeId;
    /** 所属项目业务 ID。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;
    /** 卷册标题。 */
    private String title;
    /** 在项目内的排序序号。 */
    private Integer sortOrder;
    /** 卷册描述。 */
    private String description;
    /** 创建时间。 */
    private Instant createdAt;
    /** 更新时间。 */
    private Instant updatedAt;
    /** 逻辑删除时间。 */
    private Instant deletedAt;

}

