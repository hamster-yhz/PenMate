package com.penmate.backend.domain.novel.model;

import lombok.Data;

import java.time.Instant;

@Data
public class ChapterAiUndoOperation {
    private Long id;
    private Long operationId;
    private Long projectId;
    private Long chapterId;
    private Long runId;
    private String toolCallId;
    private String beforeContent;
    private Integer beforeWordCount;
    private String resultContentHash;
    private Long sequenceNo;
    private Long appliedRevision;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;
    private Instant undoneAt;
}
