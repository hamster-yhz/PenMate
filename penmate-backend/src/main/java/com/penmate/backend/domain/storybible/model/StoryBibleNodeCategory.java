package com.penmate.backend.domain.storybible.model;

import lombok.Data;

import java.time.Instant;

@Data
public class StoryBibleNodeCategory {
    private Long id;
    private Long storyBibleId;
    private Long nodeId;
    private Long categoryId;
    private Instant createdAt;
}
