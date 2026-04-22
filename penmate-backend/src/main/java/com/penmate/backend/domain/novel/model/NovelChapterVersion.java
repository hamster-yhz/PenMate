package com.penmate.backend.domain.novel.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
/**
 * 章节历史版本实体。
 */
public class NovelChapterVersion {
    /** 版本主键 ID。 */
    private Long id;
    /** 所属章节 ID。 */
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
    /** 创建该版本的用户 ID。 */
    private Long createdBy;
    /** 版本创建时间。 */
    private LocalDateTime createdAt;

}

