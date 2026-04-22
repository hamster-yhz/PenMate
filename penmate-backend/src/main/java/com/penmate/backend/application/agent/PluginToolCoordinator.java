package com.penmate.backend.application.agent;

import com.penmate.backend.application.plugin.PluginApplicationService;
import com.penmate.backend.domain.plugin.model.PluginCallLog;
import com.penmate.backend.domain.plugin.model.PluginProjectInstall;
import com.penmate.backend.domain.shared.service.RealtimeEventService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PluginToolCoordinator {

    private final PluginApplicationService pluginApplicationService;
    private final RealtimeEventService realtimeEventService;

    public PluginToolCoordinator(PluginApplicationService pluginApplicationService,
                                 RealtimeEventService realtimeEventService) {
        this.pluginApplicationService = pluginApplicationService;
        this.realtimeEventService = realtimeEventService;
    }

    /**
     * 执行编排阶段的插件工具调用。
     * <p>当前最小实现仅取第一个启用插件，调用失败会记录日志并返回 failed，不中断主编排链路。</p>
     */
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        List<PluginProjectInstall> installs = pluginApplicationService.listProjectInstalls(request.projectId())
                .stream()
                .filter(install -> install.getEnabled() == null || install.getEnabled())
                .toList();
        if (installs.isEmpty()) {
            // 无可用插件时返回空上下文，编排继续执行 LLM。
            return ToolExecutionResult.success("", "", "");
        }

        PluginProjectInstall install = installs.get(0);
        String pluginCode = install.getPluginCode();
        String toolName = "context_enhancer";
        long startAt = System.currentTimeMillis();

        try {
            String output = buildToolOutput(request.prompt());
            int latencyMs = (int) (System.currentTimeMillis() - startAt);

            PluginCallLog callLog = new PluginCallLog();
            callLog.setProjectId(request.projectId());
            callLog.setTaskId(request.taskId());
            callLog.setPluginCode(pluginCode);
            callLog.setToolName(toolName);
            callLog.setRequestJson("{\"taskId\":" + request.taskId() + ",\"prompt\":\"" + escape(request.prompt()) + "\"}");
            callLog.setResponseJson("{\"output\":\"" + escape(output) + "\"}");
            callLog.setLatencyMs(latencyMs);
            callLog.setStatus("success");
            pluginApplicationService.recordToolCall(callLog);

            realtimeEventService.publishGenerationToolCall(
                    request.projectId(),
                    request.taskId(),
                    pluginCode,
                    toolName,
                    "success",
                    null,
                    output
            );
            return ToolExecutionResult.success(pluginCode, toolName, output);
        } catch (Exception ex) {
            // 记录失败日志并通知前端，随后让上游以空工具上下文继续生成。
            int latencyMs = (int) (System.currentTimeMillis() - startAt);
            PluginCallLog callLog = new PluginCallLog();
            callLog.setProjectId(request.projectId());
            callLog.setTaskId(request.taskId());
            callLog.setPluginCode(pluginCode);
            callLog.setToolName(toolName);
            callLog.setRequestJson("{\"taskId\":" + request.taskId() + "}");
            callLog.setResponseJson("{}");
            callLog.setLatencyMs(latencyMs);
            callLog.setStatus("failed");
            callLog.setErrorMsg(ex.getMessage());
            pluginApplicationService.recordToolCall(callLog);

            realtimeEventService.publishGenerationToolCall(
                    request.projectId(),
                    request.taskId(),
                    pluginCode,
                    toolName,
                    "failed",
                    ex.getMessage(),
                    null
            );
            return ToolExecutionResult.failed(pluginCode, toolName, ex.getMessage());
        }
    }

    /**
     * 构造工具输出内容（当前为最小占位策略）。
     */
    private String buildToolOutput(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "工具建议：补充冲突与动机线索。";
        }
        return "工具增强建议：围绕“" + prompt.trim() + "”补充细节证据与冲突钩子。";
    }

    /**
     * 对日志 JSON 字符串中的特殊字符做最小转义。
     */
    private String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}

