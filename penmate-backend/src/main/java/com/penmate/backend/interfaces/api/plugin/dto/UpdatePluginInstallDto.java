package com.penmate.backend.interfaces.api.plugin.dto;

public class UpdatePluginInstallDto {
    private Boolean enabled;
    private String configJson;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }
}

