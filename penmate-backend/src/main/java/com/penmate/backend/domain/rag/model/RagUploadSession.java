package com.penmate.backend.domain.rag.model;

import lombok.Data;

import java.time.Instant;

@Data
public class RagUploadSession {
    private Long uploadId;
    private Long projectId;
    private Long ownerUserId;
    private String docType;
    private String title;
    private String originalFilename;
    private String fileExtension;
    private String declaredMimeType;
    private Long expectedSize;
    private String expectedChecksum;
    private String objectKey;
    private String uploadTokenHash;
    private String uploadStatus;
    private Instant expiresAt;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
