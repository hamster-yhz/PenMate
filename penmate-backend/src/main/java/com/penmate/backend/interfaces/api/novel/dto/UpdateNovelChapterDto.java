package com.penmate.backend.interfaces.api.novel.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data

public class UpdateNovelChapterDto {

    private Long volumeId;
    private Long outlineNodeId;

    @NotBlank
    private String title;

    @NotNull
    private Integer chapterNo;

    private Integer status;
    private Integer wordCount;
    private String excerpt;
    private String contentObjectKey;
    private String contentEtag;
    private Long contentSize;
    private String contentChecksum;
    private String storageProvider;

}

