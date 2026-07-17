package com.penmate.backend.domain.storybible.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StoryBibleChangeset {
    private Long id;
    private Long changesetId;
    private Long storyBibleId;
    private Long contentRevision;
    private StoryBibleActorType actorType;
    private Long actorId;
    private Long sourceRunId;
    private String changeSummary;
    private LocalDateTime createdAt;
}
