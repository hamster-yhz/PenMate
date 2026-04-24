package com.penmate.backend.domain.novel.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
/**
 * 小说项目成员实体。
 */
public class NovelMember {
    /** 所属项目业务 ID。 */
    private Long projectId;
    /** 成员用户业务 ID。 */
    private Long userId;
    /** 成员角色。 */
    private String memberRole;
    /** 加入项目时间。 */
    private LocalDateTime joinedAt;

}

