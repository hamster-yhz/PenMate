package com.penmate.backend.domain.storybible.model;

import lombok.Data;

import java.time.LocalDateTime;

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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
