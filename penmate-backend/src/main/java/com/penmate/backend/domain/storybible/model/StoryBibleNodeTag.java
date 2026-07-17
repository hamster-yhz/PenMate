package com.penmate.backend.domain.storybible.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StoryBibleNodeTag {
    private Long id;
    private Long storyBibleId;
    private Long nodeId;
    private Long tagId;
    private LocalDateTime createdAt;
}
