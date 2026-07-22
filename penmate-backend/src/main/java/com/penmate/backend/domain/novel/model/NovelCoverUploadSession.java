package com.penmate.backend.domain.novel.model;

import lombok.Data;

import java.time.Instant;

@Data
public class NovelCoverUploadSession {
    private Long id;
    private Long uploadId;
    private Long projectId;
    private Long ownerUserId;
    private String operationType;
    private String originalFilename;
    private String declaredMimeType;
    private Long expectedSize;
    private String expectedChecksum;
    private String originalObjectKey;
    private String displayObjectKey;
    private String thumbnailObjectKey;
    private String uploadTokenHash;
    private Double cropX;
    private Double cropY;
    private Double cropWidth;
    private Double cropHeight;
    private Integer imageWidth;
    private Integer imageHeight;
    private String status;
    private String errorCode;
    private String errorMessage;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;
}
