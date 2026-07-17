package com.penmate.backend.domain.storybible.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StoryBibleNodeType {
    private Long id;
    private Long typeId;
    private Long storyBibleId;
    private String typeCode;
    private StoryBibleSemanticFamily semanticFamily;
    private String displayName;
    private String iconCode;
    private String fieldSchemaJson;
    private Boolean system;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime archivedAt;
}
