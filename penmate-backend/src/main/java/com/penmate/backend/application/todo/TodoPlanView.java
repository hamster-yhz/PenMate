package com.penmate.backend.application.todo;

import java.util.List;

/**
 * Todo 规划整体视图。
 */
public record TodoPlanView(
        String planTitle,
        String planSummary,
        String recommendedNextAction,
        List<TodoPlanItemView> items
) {

    public TodoPlanView {
        planTitle = planTitle == null ? "" : planTitle.trim();
        planSummary = planSummary == null ? "" : planSummary.trim();
        recommendedNextAction = recommendedNextAction == null ? "" : recommendedNextAction.trim();
        items = items == null ? List.of() : List.copyOf(items);
    }
}
