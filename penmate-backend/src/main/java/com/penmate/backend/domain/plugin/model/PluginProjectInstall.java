package com.penmate.backend.domain.plugin.model;

import java.time.LocalDateTime;

public class PluginProjectInstall {
    private Long id;
    private Long projectId;
    private Long pluginId;
    private String pluginCode;
    private String pluginName;
    private String version;
    private String configJson;
    private Boolean enabled;
    private Long installedBy;
    private LocalDateTime installedAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getPluginId() { return pluginId; }
    public void setPluginId(Long pluginId) { this.pluginId = pluginId; }
    public String getPluginCode() { return pluginCode; }
    public void setPluginCode(String pluginCode) { this.pluginCode = pluginCode; }
    public String getPluginName() { return pluginName; }
    public void setPluginName(String pluginName) { this.pluginName = pluginName; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Long getInstalledBy() { return installedBy; }
    public void setInstalledBy(Long installedBy) { this.installedBy = installedBy; }
    public LocalDateTime getInstalledAt() { return installedAt; }
    public void setInstalledAt(LocalDateTime installedAt) { this.installedAt = installedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

