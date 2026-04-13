package com.penmate.backend.application.plugin.command;

public final class PluginCommands {

    private PluginCommands() {
    }

    public record InstallPluginCommand(String pluginCode,
                                       String version,
                                       String configJson,
                                       Long operatorId) {
    }

    public record UpdatePluginInstallCommand(Boolean enabled,
                                             String configJson,
                                             Long operatorId) {
    }
}

