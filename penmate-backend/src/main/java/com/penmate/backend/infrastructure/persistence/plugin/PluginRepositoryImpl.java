package com.penmate.backend.infrastructure.persistence.plugin;

import com.penmate.backend.domain.plugin.model.PluginCallLog;
import com.penmate.backend.domain.plugin.model.PluginCatalogItem;
import com.penmate.backend.domain.plugin.model.PluginProjectInstall;
import com.penmate.backend.domain.plugin.repository.PluginRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 插件仓储 MyBatis 实现。
 * <p>负责插件目录、项目安装记录与插件调用日志的持久化读写。</p>
 */
@Repository
public class PluginRepositoryImpl implements PluginRepository {

    private final PluginMapper pluginMapper;

    public PluginRepositoryImpl(PluginMapper pluginMapper) {
        this.pluginMapper = pluginMapper;
    }

    /**
     * 查询插件目录列表。
     * <p>流程：读取可安装插件目录集合。</p>
     */
    @Override
    public List<PluginCatalogItem> listCatalog() {
        return pluginMapper.listCatalog();
    }

    /**
     * 查询插件目录详情。
     * <p>流程：按插件编码读取目录项详情。</p>
     */
    @Override
    public PluginCatalogItem getCatalogByCode(String pluginCode) {
        return pluginMapper.getCatalogByCode(pluginCode);
    }

    /**
     * 查询插件目录主键ID。
     * <p>流程：按编码返回插件目录ID，供安装表外键写入。</p>
     */
    @Override
    public Long findCatalogIdByCode(String pluginCode) {
        return pluginMapper.findCatalogIdByCode(pluginCode);
    }

    /**
     * 查询项目安装插件列表。
     * <p>流程：按项目ID读取插件安装记录。</p>
     */
    @Override
    public List<PluginProjectInstall> listProjectInstalls(Long projectId) {
        return pluginMapper.listProjectInstalls(projectId);
    }

    /**
     * 新增插件安装记录。
     * <p>流程：写入项目与插件绑定关系、版本、配置与启用状态。</p>
     */
    @Override
    public int insertInstall(Long pluginInstallId,
                             Long projectId,
                             Long pluginId,
                             String version,
                             String configJson,
                             boolean enabled,
                             Long installedBy) {
        return pluginMapper.insertInstall(pluginInstallId, projectId, pluginId, version, configJson, enabled, installedBy);
    }

    /**
     * 更新插件安装配置。
     * <p>流程：按项目+插件编码更新启用状态与配置JSON。</p>
     */
    @Override
    public int updateInstall(Long projectId, String pluginCode, Boolean enabled, String configJson) {
        return pluginMapper.updateInstall(projectId, pluginCode, enabled, configJson);
    }

    /**
     * 删除插件安装记录。
     * <p>流程：按项目与插件编码删除安装绑定。</p>
     */
    @Override
    public int deleteInstall(Long projectId, String pluginCode) {
        return pluginMapper.deleteInstall(projectId, pluginCode);
    }

    /**
     * 查询插件调用日志列表。
     * <p>流程：按项目ID读取插件调用轨迹。</p>
     */
    @Override
    public List<PluginCallLog> listCallLogs(Long projectId) {
        return pluginMapper.listCallLogs(projectId);
    }

    /**
     * 新增插件调用日志。
     * <p>流程：记录插件执行输入/输出与状态，供审计与排障。</p>
     */
    @Override
    public int insertCallLog(PluginCallLog callLog) {
        return pluginMapper.insertCallLog(callLog);
    }
}

