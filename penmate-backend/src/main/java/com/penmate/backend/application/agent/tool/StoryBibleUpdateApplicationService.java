package com.penmate.backend.application.agent.tool;

import com.penmate.backend.application.agent.tool.runtime.AuthorizedAgentRunContext;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;

import java.util.Map;
import java.util.List;

/**
 * 故事圣经更新工具应用服务。
 */
public interface StoryBibleUpdateApplicationService {

    ToolCallResult execute(AuthorizedAgentRunContext context, String mutationKind, Map<String, Object> mutation);

    ToolCallResult executeBatch(AuthorizedAgentRunContext context, List<MutationCommand> mutations);

    record MutationCommand(String mutationKind, Map<String, Object> mutation) {
        public MutationCommand {
            mutation = Map.copyOf(mutation == null ? Map.of() : mutation);
        }
    }
}
