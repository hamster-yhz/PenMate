package com.penmate.backend.domain.storybible.model;

import lombok.Data;

import java.time.Instant;

@Data
public class StoryBibleProgression {
    private Long id;
    private Long progressionId;
    private Long storyBibleId;
    private Long nodeId;
    private Long anchorChapterId;
    private Long endChapterId;
    private Long storyEventNodeId;
    private String patchJson;
    private String summary;
    private Long revision;
    private Long createdBy;
    private Long updatedBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
}
