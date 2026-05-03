package com.penmate.backend.application.agent.tool.handler;

import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.tool.plugin.PluginToolExecuteCommand;
import com.penmate.backend.application.agent.tool.plugin.PluginToolExecuteResult;
import com.penmate.backend.application.agent.tool.plugin.PluginToolExecutor;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.infrastructure.agent.codec.AgentJsonCodec;
import org.springframework.stereotype.Component;

@Component
public class ContextEnhancerToolHandler implements AgentToolHandler {

    private final PluginToolExecutor pluginToolExecutor;

    public ContextEnhancerToolHandler(PluginToolExecutor pluginToolExecutor) {
        this.pluginToolExecutor = pluginToolExecutor;
    }

    @Override
    public String toolCode() {
        return "context_enhancer";
    }

    @Override
    public void validate(ToolCallRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
    }

    @Override
    public ToolCallResult execute(ToolCallRequest request) {
        try {
            JSONObject args = AgentJsonCodec.parseObj(request.toolArgsJson());
            String prompt = AgentJsonCodec.getString(args, "prompt");
            PluginToolExecuteResult result = pluginToolExecutor.execute(new PluginToolExecuteCommand(
                    request.projectId(),
                    request.taskId(),
                    prompt,
                    request.traceId()
            ));
            if (!result.success()) {
                return new ToolCallResult("FAILED", null, null, "PLUGIN_TOOL_FAILED", result.output());
            }
            return ToolCallResult.success(result.output());
        } catch (Exception ex) {
            return new ToolCallResult("FAILED", null, null, "CONTEXT_ENHANCER_FAILED", ex.getMessage());
        }
    }
}
