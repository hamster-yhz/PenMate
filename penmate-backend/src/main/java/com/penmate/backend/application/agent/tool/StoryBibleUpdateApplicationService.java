package com.penmate.backend.application.agent.tool;

import com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;

/**
 * 故事圣经更新工具应用服务。
 */
public interface StoryBibleUpdateApplicationService {

    ToolCallResult execute(AuthorizedAgentRunContext context, ToolCallRequest request);
}
