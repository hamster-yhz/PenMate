package com.penmate.backend.application.agent.runtime;

import java.util.Map;

/**
 * 任务运行态视图。
 * <p>字段与 recovery/runtime contract 对齐，避免事件与恢复快照出现两套状态语义。</p>
 */
public record RuntimeStatusView(
        Long taskId,
        Long sessionId,
        Long turnId,
        String phase,
        String message,
        ToolCallStatusView toolCall,
        Map<String, Object> approval,
        StoryBibleApprovalView storyBibleApproval,
        boolean recoverable,
        String nextAction
) {
}
