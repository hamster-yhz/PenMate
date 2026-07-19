package com.penmate.backend.domain.novel.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.time.Instant;

@Data
/**
 * 章节历史版本实体。
 */
public class NovelChapterVersion {
    /** 数据库物理主键 ID。 */
    private Long id;
    /** 章节版本业务 ID。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chapterVersionId;

    /** 所属章节业务 ID。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chapterId;
    /** 版本号。 */
    private Integer versionNo;
    /** 变更类型。 */
    private String changeType;
    /** 变更原因。 */
    private String changeReason;
    /** 快照对象存储键。 */
    private String snapshotObjectKey;
    /** 快照对象 ETag。 */
    private String snapshotEtag;
    /** 快照大小（字节）。 */
    private Long snapshotSize;
    /** 快照校验和。 */
    private String snapshotChecksum;
    /** 创建该版本的用户业务 ID。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;
    /** 版本创建时间。 */
    private Instant createdAt;

}

