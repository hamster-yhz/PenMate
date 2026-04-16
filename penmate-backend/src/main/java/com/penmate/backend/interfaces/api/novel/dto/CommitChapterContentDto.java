package com.penmate.backend.interfaces.api.novel.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data

public class CommitChapterContentDto {

    @NotBlank
    private String objectKey;
    private String etag;
    private Long size;
    private String checksum;
    private String storageProvider;

}

