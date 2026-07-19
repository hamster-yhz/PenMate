package com.penmate.backend.domain.storybible.model;

import lombok.Data;

import java.time.Instant;

@Data
public class StoryBibleRelation {
    private Long id;
    private Long relationId;
    private Long storyBibleId;
    private Long sourceNodeId;
    private String relationType;
    private Long targetNodeId;
    private String description;
    private String attributesJson;
    private Long revision;
    private Long createdBy;
    private Long updatedBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
}
