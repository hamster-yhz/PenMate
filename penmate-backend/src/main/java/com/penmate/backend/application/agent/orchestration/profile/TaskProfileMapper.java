package com.penmate.backend.application.agent.orchestration.profile;

import com.penmate.backend.application.agent.orchestration.preflight.AgentBehaviorType;
import com.penmate.backend.application.agent.context.AgentRouteDecision;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Maps preflight decision output into the stable {@link TaskProfile} contract.
 */
public final class TaskProfileMapper {

    private TaskProfileMapper() {
    }

    public static TaskProfile from(AgentRouteDecision decision) {
        Objects.requireNonNull(decision, "decision");
        Set<TaskIntentTag> intentTags = new LinkedHashSet<>();
        for (String rawTag : decision.intentTags()) {
            intentTags.add(parseIntentTag(rawTag));
        }
        if (decision.behaviorType() == AgentBehaviorType.STORY_BIBLE_QUERY_CANDIDATE) {
            intentTags.add(TaskIntentTag.STORY_BIBLE_QUERY);
        }
        if (decision.needsClarification()) {
            intentTags.add(TaskIntentTag.CLARIFICATION);
        }
        boolean includeStoryBible = decision.includeStoryBibleContext() || decision.needsStoryBibleUpdate();
        return new TaskProfile(
                new ArrayList<>(intentTags),
                decision.executionPromptProfile(),
                decision.enabledSkills(),
                decision.enabledTools(),
                decision.hardConstraints(),
                decision.outputExpectation(),
                decision.needsApproval(),
                includeStoryBible,
                decision.includeRagContext(),
                decision.reasoningSummary()
        );
    }

    private static TaskIntentTag parseIntentTag(String rawTag) {
        try {
            return TaskIntentTag.valueOf(rawTag.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Unsupported intent tag: " + rawTag, ex);
        }
    }
}
