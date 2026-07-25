package com.penmate.backend.domain.author.model;

import lombok.Data;

import java.time.Instant;

@Data
public class AuthorProfile {
    private Long id;
    private Long userId;
    private String defaultLanguage;
    private String collaborationMode;
    private String defaultPov;
    private String defaultTense;
    private String descriptionDensity;
    private String dialoguePreference;
    private String bannedExpressions;
    private String longTermMemory;
    private Instant createdAt;
    private Instant updatedAt;
}
