package com.penmate.backend.application.agent;

import cn.hutool.json.JSONObject;
import com.penmate.backend.application.agent.json.AgentJsons;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ContextEnhancerAgentToolHandler implements AgentToolHandler {

    private final PluginToolCoordinator pluginToolCoordinator;

    public ContextEnhancerAgentToolHandler(PluginToolCoordinator pluginToolCoordinator) {
        this.pluginToolCoordinator = pluginToolCoordinator;
    }

    @Override
    public String toolCode() {
        return "context_enhancer";
    }

    @Override
    public ToolInvocationGatewayResult execute(ToolInvocationRequest request) {
        try {
            JSONObject root = AgentJsons.parseObj(request.toolArgsJson());
            String prompt = AgentJsons.getString(root, "prompt");
            log.info("执行 context_enhancer 工具: projectId={}, taskId={}, promptLength={}, traceId={}",
                    request.projectId(), request.taskId(), prompt.length(), request.traceId());
            ToolExecutionResult result = pluginToolCoordinator.execute(new ToolExecutionRequest(
                    request.projectId(),
                    request.taskId(),
                    prompt,
                    request.traceId()
            ));
            if (result.success()) {
                log.info("context_enhancer 执行成功: taskId={}, traceId={}", request.taskId(), request.traceId());
                return ToolInvocationGatewayResult.success(result.output());
            }
            log.warn("context_enhancer 执行失败: taskId={}, traceId={}, message={}", request.taskId(), request.traceId(), result.errorMsg());
            return new ToolInvocationGatewayResult("FAILED", null, null, "TOOL_EXECUTION_FAILED", result.errorMsg());
        } catch (Exception ex) {
            log.error("执行 context_enhancer 工具异常: taskId={}, traceId={}", request.taskId(), request.traceId(), ex);
            throw new IllegalArgumentException("Failed to execute context_enhancer tool", ex);
        }
    }
}
