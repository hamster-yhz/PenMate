package com.penmate.backend.domain.novel.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data

public class NovelChapterVersion {
    private Long id;
    private Long chapterId;
    private Integer versionNo;
    private String changeType;
    private String changeReason;
    private String snapshotObjectKey;
    private String snapshotEtag;
    private Long snapshotSize;
    private String snapshotChecksum;
    private Long createdBy;
    private LocalDateTime createdAt;

}

