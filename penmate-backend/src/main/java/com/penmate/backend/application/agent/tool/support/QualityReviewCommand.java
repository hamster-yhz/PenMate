package com.penmate.backend.application.agent.tool.support;

import java.util.List;

public record QualityReviewCommand(
        String draftText,
        List<String> userRequirements,
        List<String> personaProfile,
        List<String> storyOutline,
        List<String> timelineConstraints,
        List<String> worldRules,
        List<String> characterKnowledgeBoundaries,
        int currentRevisionRound,
        int maxRevisionRounds
) {
    public QualityReviewCommand {
        draftText = draftText == null ? "" : draftText.trim();
        userRequirements = userRequirements == null ? List.of() : List.copyOf(userRequirements);
        personaProfile = personaProfile == null ? List.of() : List.copyOf(personaProfile);
        storyOutline = storyOutline == null ? List.of() : List.copyOf(storyOutline);
        timelineConstraints = timelineConstraints == null ? List.of() : List.copyOf(timelineConstraints);
        worldRules = worldRules == null ? List.of() : List.copyOf(worldRules);
        characterKnowledgeBoundaries = characterKnowledgeBoundaries == null ? List.of() : List.copyOf(characterKnowledgeBoundaries);
    }
}
