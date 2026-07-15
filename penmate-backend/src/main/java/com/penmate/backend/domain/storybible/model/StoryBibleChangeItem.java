package com.penmate.backend.domain.storybible.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StoryBibleChangeItem {
    private Long id;
    private Long changeItemId;
    private Long changesetId;
    private String entityType;
    private Long entityId;
    private StoryBibleChangeOperation operation;
    private String fieldPath;
    private String beforeJson;
    private String afterJson;
    private LocalDateTime createdAt;
}
