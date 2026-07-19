package com.penmate.backend.domain.storybible.model;

import lombok.Data;

import java.time.Instant;

@Data
public class StoryBibleTag {
    private Long id;
    private Long tagId;
    private Long storyBibleId;
    private String name;
    private String normalizedName;
    private String color;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
}
