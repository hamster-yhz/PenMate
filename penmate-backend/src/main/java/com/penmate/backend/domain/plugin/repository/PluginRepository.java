package com.penmate.backend.domain.plugin.repository;

import com.penmate.backend.domain.plugin.model.PluginCallLog;
import com.penmate.backend.domain.plugin.model.PluginCatalogItem;
import com.penmate.backend.domain.plugin.model.PluginProjectInstall;

import java.util.List;

public interface PluginRepository {

    List<PluginCatalogItem> listCatalog();

    PluginCatalogItem getCatalogByCode(String pluginCode);

    Long findCatalogIdByCode(String pluginCode);

    List<PluginProjectInstall> listProjectInstalls(Long projectId);

    int insertInstall(Long projectId,
                      Long pluginId,
                      String version,
                      String configJson,
                      boolean enabled,
                      Long installedBy);

    int updateInstall(Long projectId,
                      String pluginCode,
                      Boolean enabled,
                      String configJson);

    int deleteInstall(Long projectId, String pluginCode);

    List<PluginCallLog> listCallLogs(Long projectId);

    int insertCallLog(PluginCallLog callLog);
}

