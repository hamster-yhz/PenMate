package com.penmate.backend.domain.novel.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data

public class NovelMember {
    private Long projectId;
    private Long userId;
    private String memberRole;
    private LocalDateTime joinedAt;

}

