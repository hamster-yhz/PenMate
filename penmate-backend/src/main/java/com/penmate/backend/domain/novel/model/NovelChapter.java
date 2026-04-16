package com.penmate.backend.domain.novel.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data

public class NovelChapter {
    private Long id;
    private Long projectId;
    private Long volumeId;
    private Long outlineNodeId;
    private String title;
    private Integer chapterNo;
    private Integer status;
    private Integer wordCount;
    private String excerpt;
    private String contentObjectKey;
    private String contentEtag;
    private Long contentSize;
    private String contentChecksum;
    private String storageProvider;
    private LocalDateTime lastGeneratedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

}

