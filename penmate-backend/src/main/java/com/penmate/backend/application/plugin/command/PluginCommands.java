package com.penmate.backend.application.plugin.command;

public final class PluginCommands {

    private PluginCommands() {
    }

    /**
     * InstallPluginCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record InstallPluginCommand(String pluginCode,
                                       String version,
                                       String configJson,
                                       Long operatorId) {
    }

    /**
     * UpdatePluginInstallCommand。
     * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
     */
    public record UpdatePluginInstallCommand(Boolean enabled,
                                             String configJson,
                                             Long operatorId) {
    }
}

