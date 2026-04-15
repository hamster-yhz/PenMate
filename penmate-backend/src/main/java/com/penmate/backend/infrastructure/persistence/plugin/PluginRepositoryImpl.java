package com.penmate.backend.infrastructure.persistence.plugin;

import com.penmate.backend.domain.plugin.model.PluginCallLog;
import com.penmate.backend.domain.plugin.model.PluginCatalogItem;
import com.penmate.backend.domain.plugin.model.PluginProjectInstall;
import com.penmate.backend.domain.plugin.repository.PluginRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * PluginRepositoryImpl。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Repository
public class PluginRepositoryImpl implements PluginRepository {

    private final PluginMapper pluginMapper;

    public PluginRepositoryImpl(PluginMapper pluginMapper) {
        this.pluginMapper = pluginMapper;
    }

    /**
     * 查询列表数据。
     *
     * @return 出参：处理结果
     */
    @Override
    public List<PluginCatalogItem> listCatalog() {
        return pluginMapper.listCatalog();
    }

    /**
     * 查询详情数据。
     *
     * @param pluginCode 入参：pluginCode
     * @return 出参：处理结果
     */
    @Override
    public PluginCatalogItem getCatalogByCode(String pluginCode) {
        return pluginMapper.getCatalogByCode(pluginCode);
    }

    /**
     * 处理业务请求。
     *
     * @param pluginCode 入参：pluginCode
     * @return 出参：处理结果
     */
    @Override
    public Long findCatalogIdByCode(String pluginCode) {
        return pluginMapper.findCatalogIdByCode(pluginCode);
    }

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    @Override
    public List<PluginProjectInstall> listProjectInstalls(Long projectId) {
        return pluginMapper.listProjectInstalls(projectId);
    }

    /**
     * 处理业务请求。
     *
     * @param projectId 入参：projectId
     * @param pluginId 入参：pluginId
     * @param version 入参：version
     * @param configJson 入参：configJson
     * @param enabled 入参：enabled
     * @param installedBy 入参：installedBy
     * @return 出参：处理结果
     */
    @Override
    public int insertInstall(Long projectId,
                             Long pluginId,
                             String version,
                             String configJson,
                             boolean enabled,
                             Long installedBy) {
        return pluginMapper.insertInstall(projectId, pluginId, version, configJson, enabled, installedBy);
    }

    /**
     * 更新业务数据。
     *
     * @param projectId 入参：projectId
     * @param pluginCode 入参：pluginCode
     * @param enabled 入参：enabled
     * @param configJson 入参：configJson
     * @return 出参：处理结果
     */
    @Override
    public int updateInstall(Long projectId, String pluginCode, Boolean enabled, String configJson) {
        return pluginMapper.updateInstall(projectId, pluginCode, enabled, configJson);
    }

    /**
     * 删除业务数据。
     *
     * @param projectId 入参：projectId
     * @param pluginCode 入参：pluginCode
     * @return 出参：处理结果
     */
    @Override
    public int deleteInstall(Long projectId, String pluginCode) {
        return pluginMapper.deleteInstall(projectId, pluginCode);
    }

    /**
     * 查询列表数据。
     *
     * @param projectId 入参：projectId
     * @return 出参：处理结果
     */
    @Override
    public List<PluginCallLog> listCallLogs(Long projectId) {
        return pluginMapper.listCallLogs(projectId);
    }
}

