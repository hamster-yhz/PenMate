package com.penmate.backend.domain.storybible.model;

import lombok.Data;

import java.time.Instant;

@Data
public class StoryBibleNode {
    private Long id;
    private Long nodeId;
    private Long storyBibleId;
    private Long typeId;
    private String title;
    private String summary;
    private String bodyMarkdown;
    private String attributesJson;
    private StoryBibleInclusionPolicy inclusionPolicy;
    private StoryBibleCanonStatus canonStatus;
    private Long revision;
    private Long createdBy;
    private Long updatedBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant archivedAt;
    private Instant deletedAt;
}
