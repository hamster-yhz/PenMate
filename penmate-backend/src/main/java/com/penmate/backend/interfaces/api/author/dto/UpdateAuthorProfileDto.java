package com.penmate.backend.interfaces.api.author.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAuthorProfileDto(
        @NotBlank @Size(max = 32) String defaultLanguage,
        @NotBlank @Size(max = 32) String collaborationMode,
        @NotBlank @Size(max = 32) String defaultPov,
        @NotBlank @Size(max = 32) String defaultTense,
        @NotBlank @Size(max = 16) String descriptionDensity,
        @Size(max = 1000) String dialoguePreference,
        @Size(max = 2000) String bannedExpressions,
        @Size(max = 5000) String longTermMemory
) {
}
