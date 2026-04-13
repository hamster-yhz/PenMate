package com.penmate.backend.infrastructure.persistence.plugin;

import com.penmate.backend.domain.plugin.model.PluginCallLog;
import com.penmate.backend.domain.plugin.model.PluginCatalogItem;
import com.penmate.backend.domain.plugin.model.PluginProjectInstall;
import com.penmate.backend.domain.plugin.repository.PluginRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PluginRepositoryImpl implements PluginRepository {

    private final PluginMapper pluginMapper;

    public PluginRepositoryImpl(PluginMapper pluginMapper) {
        this.pluginMapper = pluginMapper;
    }

    @Override
    public List<PluginCatalogItem> listCatalog() {
        return pluginMapper.listCatalog();
    }

    @Override
    public PluginCatalogItem getCatalogByCode(String pluginCode) {
        return pluginMapper.getCatalogByCode(pluginCode);
    }

    @Override
    public Long findCatalogIdByCode(String pluginCode) {
        return pluginMapper.findCatalogIdByCode(pluginCode);
    }

    @Override
    public List<PluginProjectInstall> listProjectInstalls(Long projectId) {
        return pluginMapper.listProjectInstalls(projectId);
    }

    @Override
    public int insertInstall(Long projectId,
                             Long pluginId,
                             String version,
                             String configJson,
                             boolean enabled,
                             Long installedBy) {
        return pluginMapper.insertInstall(projectId, pluginId, version, configJson, enabled, installedBy);
    }

    @Override
    public int updateInstall(Long projectId, String pluginCode, Boolean enabled, String configJson) {
        return pluginMapper.updateInstall(projectId, pluginCode, enabled, configJson);
    }

    @Override
    public int deleteInstall(Long projectId, String pluginCode) {
        return pluginMapper.deleteInstall(projectId, pluginCode);
    }

    @Override
    public List<PluginCallLog> listCallLogs(Long projectId) {
        return pluginMapper.listCallLogs(projectId);
    }
}

