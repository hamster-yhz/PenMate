package com.penmate.backend.application.agent.tool;

import com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;

import java.util.Map;

/**
 * 故事圣经更新工具应用服务。
 */
public interface StoryBibleUpdateApplicationService {

    ToolCallResult execute(AuthorizedAgentRunContext context, String mutationKind, Map<String, Object> mutation);
}
