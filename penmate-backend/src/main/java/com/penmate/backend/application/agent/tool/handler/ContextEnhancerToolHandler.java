package com.penmate.backend.application.agent.tool.handler;

import com.penmate.backend.application.agent.tool.plugin.PluginToolExecuteCommand;
import com.penmate.backend.application.agent.tool.plugin.PluginToolExecuteResult;
import com.penmate.backend.application.agent.tool.plugin.PluginToolExecutor;
import com.penmate.backend.application.agent.tool.runtime.ToolCallRequest;
import com.penmate.backend.application.agent.tool.runtime.ToolCallResult;
import com.penmate.backend.application.common.serialization.JsonCodec;
import com.penmate.backend.application.common.serialization.JsonValues;
import org.springframework.stereotype.Component;

/**
 * 上下文增强 tool 处理器。
 * <p>该 handler 将 LLM 传入的增强提示词转交给 {@link PluginToolExecutor}，把插件执行结果作为 tool 输出返回给 loop。</p>
 * <p>它展示的是“轻量代理外部执行器”的实现模式，与 {@code book_crud} 这种直接编排领域应用服务的复合工具不同。</p>
 */
@Component
public class ContextEnhancerToolHandler implements AgentToolHandler {

    private final PluginToolExecutor pluginToolExecutor;
    private final JsonCodec jsonCodec;

    public ContextEnhancerToolHandler(PluginToolExecutor pluginToolExecutor, JsonCodec jsonCodec) {
        this.pluginToolExecutor = pluginToolExecutor;
        this.jsonCodec = jsonCodec;
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
            String prompt = JsonValues.string(jsonCodec.readObject(request.toolArgsJson()), "prompt");
            PluginToolExecuteResult result = pluginToolExecutor.execute(new PluginToolExecuteCommand(
                    request.projectId(),
                    request.runId(),
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
