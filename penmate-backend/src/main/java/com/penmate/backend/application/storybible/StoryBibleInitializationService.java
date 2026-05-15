package com.penmate.backend.application.storybible;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StoryBibleInitializationService {

    public List<StoryBibleProposalItem> initializeFromIdea(Long projectId, String idea) {
        String normalizedIdea = normalize(idea);
        if (normalizedIdea.isEmpty()) {
            return List.of();
        }
        List<StoryBibleProposalItem> proposals = new ArrayList<>();
        if (containsAny(normalizedIdea, "守夜人", "失忆", "名字")) {
            proposals.add(new StoryBibleProposalItem(
                    "character.idea.protagonist",
                    "character",
                    "主角是失忆的守夜人，并在追索自我身份。",
                    "PROPOSED",
                    2,
                    normalizedIdea,
                    null,
                    "IDEA_DERIVED"
            ));
        }
        if (normalizedIdea.contains("灯塔")) {
            proposals.add(new StoryBibleProposalItem(
                    "location.idea.lighthouse",
                    "location",
                    normalizedIdea.contains("会说话") ? "故事核心地点是一座会说话的灯塔。" : "故事核心地点是一座关键灯塔。",
                    "PROPOSED",
                    2,
                    normalizedIdea,
                    null,
                    "IDEA_DERIVED"
            ));
        }
        if (containsAny(normalizedIdea, "寻找", "名字")) {
            proposals.add(new StoryBibleProposalItem(
                    "event.idea.quest",
                    "event",
                    "主线目标是寻找主角失落的名字与身份。",
                    "PROPOSED",
                    2,
                    normalizedIdea,
                    null,
                    "IDEA_DERIVED"
            ));
        }
        if (proposals.isEmpty()) {
            proposals.add(new StoryBibleProposalItem(
                    "story.idea.seed",
                    "event",
                    normalizedIdea,
                    "PROPOSED",
                    1,
                    normalizedIdea,
                    null,
                    "IDEA_DERIVED"
            ));
        }
        return List.copyOf(proposals);
    }

    private boolean containsAny(String text, String... markers) {
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
