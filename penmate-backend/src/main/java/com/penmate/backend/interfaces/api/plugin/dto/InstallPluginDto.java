package com.penmate.backend.interfaces.api.plugin.dto;

import jakarta.validation.constraints.NotBlank;

public class InstallPluginDto {

    @NotBlank
    private String pluginCode;
    private String version;
    private String configJson;

    public String getPluginCode() {
        return pluginCode;
    }

    public void setPluginCode(String pluginCode) {
        this.pluginCode = pluginCode;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }
}

