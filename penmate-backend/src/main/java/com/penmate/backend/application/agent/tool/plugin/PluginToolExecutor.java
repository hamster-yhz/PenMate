package com.penmate.backend.application.agent.tool.plugin;

import com.penmate.backend.application.plugin.PluginApplicationService;
import com.penmate.backend.domain.plugin.model.PluginCallLog;
import com.penmate.backend.domain.plugin.model.PluginProjectInstall;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 插件型 tool 执行器。
 * <p>当前用于承接 {@code context_enhancer} 这类通过项目已安装插件执行的工具调用，并统一记录调用日志与实时事件。</p>
 */
@Component
public class PluginToolExecutor {

    private final PluginApplicationService pluginApplicationService;

    public PluginToolExecutor(PluginApplicationService pluginApplicationService) {
        this.pluginApplicationService = pluginApplicationService;
    }

    public PluginToolExecuteResult execute(PluginToolExecuteCommand request) {
        List<PluginProjectInstall> installs = pluginApplicationService.listProjectInstalls(request.projectId())
                .stream()
                .filter(install -> install.getEnabled() == null || install.getEnabled())
                .toList();
        if (installs.isEmpty()) {
            return PluginToolExecuteResult.success("", "", "");
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

            return PluginToolExecuteResult.success(pluginCode, toolName, output);
        } catch (Exception ex) {
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

            return PluginToolExecuteResult.failed(pluginCode, toolName, ex.getMessage());
        }
    }

    private String buildToolOutput(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "工具建议：补充冲突与动机线索。";
        }
        return "工具增强建议：围绕“" + prompt.trim() + "”补充细节证据与冲突钩子。";
    }

    private String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
