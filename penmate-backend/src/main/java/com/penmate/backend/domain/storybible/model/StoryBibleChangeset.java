package com.penmate.backend.domain.storybible.model;

import lombok.Data;

import java.time.Instant;

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
    private Instant createdAt;
    private Instant archivedAt;
    private Instant undoneAt;
    private Long undoneBy;
    private Long undoChangesetId;
}
