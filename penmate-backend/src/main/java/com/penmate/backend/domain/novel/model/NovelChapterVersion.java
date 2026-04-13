package com.penmate.backend.domain.novel.model;

import java.time.LocalDateTime;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChapterId() { return chapterId; }
    public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
    public String getSnapshotObjectKey() { return snapshotObjectKey; }
    public void setSnapshotObjectKey(String snapshotObjectKey) { this.snapshotObjectKey = snapshotObjectKey; }
    public String getSnapshotEtag() { return snapshotEtag; }
    public void setSnapshotEtag(String snapshotEtag) { this.snapshotEtag = snapshotEtag; }
    public Long getSnapshotSize() { return snapshotSize; }
    public void setSnapshotSize(Long snapshotSize) { this.snapshotSize = snapshotSize; }
    public String getSnapshotChecksum() { return snapshotChecksum; }
    public void setSnapshotChecksum(String snapshotChecksum) { this.snapshotChecksum = snapshotChecksum; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

