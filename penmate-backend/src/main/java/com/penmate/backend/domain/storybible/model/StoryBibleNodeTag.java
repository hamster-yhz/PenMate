package com.penmate.backend.domain.storybible.model;

import lombok.Data;

import java.time.Instant;

@Data
public class StoryBibleNodeTag {
    private Long id;
    private Long storyBibleId;
    private Long nodeId;
    private Long tagId;
    private Instant createdAt;
}
