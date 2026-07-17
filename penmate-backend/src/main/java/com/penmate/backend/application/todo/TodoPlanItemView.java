package com.penmate.backend.application.todo;

import java.util.List;

/**
 * Todo 规划项视图。
 */
public record TodoPlanItemView(
        String title,
        String description,
        String priority,
        String sourceType,
        String recommendedStatus,
        boolean suggestedAutoCreate,
        String rationale,
        List<String> acceptanceCriteria,
        List<String> dependsOn
) {

    public TodoPlanItemView {
        title = title == null ? "" : title.trim();
        description = description == null ? "" : description.trim();
        priority = priority == null ? "" : priority.trim();
        sourceType = sourceType == null ? "" : sourceType.trim();
        recommendedStatus = recommendedStatus == null ? "" : recommendedStatus.trim();
        rationale = rationale == null ? "" : rationale.trim();
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }
}
