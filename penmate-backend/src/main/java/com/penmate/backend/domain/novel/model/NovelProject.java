package com.penmate.backend.domain.novel.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
/**
 * 小说项目实体。
 */
public class NovelProject {
    /** 项目主键 ID。 */
    private Long id;
    /** 项目拥有者用户 ID。 */
    private Long ownerUserId;
    /** 小说项目标题。 */
    private String title;
    /** 项目简介。 */
    private String summary;
    /** 项目状态。 */
    private Integer status;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除时间。 */
    private LocalDateTime deletedAt;

}

