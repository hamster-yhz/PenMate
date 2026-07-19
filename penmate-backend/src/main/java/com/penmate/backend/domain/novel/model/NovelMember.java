package com.penmate.backend.domain.novel.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.time.Instant;

@Data
/**
 * 小说项目成员实体。
 */
public class NovelMember {
    /** 所属项目业务 ID。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long projectId;
    /** 成员用户业务 ID。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    /** 成员角色。 */
    private String memberRole;
    /** 加入项目时间。 */
    private Instant joinedAt;

}

