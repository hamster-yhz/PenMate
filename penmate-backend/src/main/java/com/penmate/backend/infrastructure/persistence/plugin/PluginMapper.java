package com.penmate.backend.infrastructure.persistence.plugin;

import com.penmate.backend.domain.plugin.model.PluginCallLog;
import com.penmate.backend.domain.plugin.model.PluginCatalogItem;
import com.penmate.backend.domain.plugin.model.PluginProjectInstall;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * PluginMapper。
 * <p>基建层：负责持久化、实时通信、配置与外部依赖实现。</p>
 */
@Mapper
public interface PluginMapper {

    @Select("""
            SELECT id, plugin_id, code, name, category, provider, status, latest_version, created_at, updated_at
            FROM plugin_catalog
            ORDER BY id DESC
            """)
    List<PluginCatalogItem> listCatalog();

    @Select("""
            SELECT id, plugin_id, code, name, category, provider, status, latest_version, created_at, updated_at
            FROM plugin_catalog
            WHERE code = #{pluginCode}
            LIMIT 1
            """)
    PluginCatalogItem getCatalogByCode(@Param("pluginCode") String pluginCode);

    @Select("""
            SELECT plugin_id
            FROM plugin_catalog
            WHERE code = #{pluginCode}
            LIMIT 1
            """)
    Long findCatalogIdByCode(@Param("pluginCode") String pluginCode);

    @Select("""
            SELECT i.id, i.plugin_install_id, i.project_id, i.plugin_id, c.code AS plugin_code, c.name AS plugin_name,
                   i.version, CAST(i.config_json AS TEXT) AS config_json,
                   i.enabled, i.installed_by, i.installed_at, i.updated_at
            FROM plugin_project_installs i
            JOIN plugin_catalog c ON c.plugin_id = i.plugin_id
            WHERE i.project_id = #{projectId}
            ORDER BY i.id DESC
            """)
    List<PluginProjectInstall> listProjectInstalls(@Param("projectId") Long projectId);

    @Insert("""
            INSERT INTO plugin_project_installs(plugin_install_id, project_id, plugin_id, version, config_json, enabled, installed_by)
            VALUES (#{pluginInstallId}, #{projectId}, #{pluginId}, #{version}, #{configJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler}, #{enabled}, #{installedBy})
            ON CONFLICT (project_id, plugin_id) DO UPDATE SET
                version = EXCLUDED.version,
                config_json = EXCLUDED.config_json,
                enabled = EXCLUDED.enabled,
                updated_at = CURRENT_TIMESTAMP(3)
            """)
    int insertInstall(@Param("pluginInstallId") Long pluginInstallId,
                      @Param("projectId") Long projectId,
                      @Param("pluginId") Long pluginId,
                      @Param("version") String version,
                      @Param("configJson") String configJson,
                      @Param("enabled") boolean enabled,
                      @Param("installedBy") Long installedBy);

    @Update("""
            UPDATE plugin_project_installs i
            JOIN plugin_catalog c ON c.plugin_id = i.plugin_id
            SET i.enabled = COALESCE(#{enabled}, i.enabled),
                i.config_json = COALESCE(#{configJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler}, i.config_json),
                i.updated_at = CURRENT_TIMESTAMP(3)
            WHERE i.project_id = #{projectId} AND c.code = #{pluginCode}
            """)
    int updateInstall(@Param("projectId") Long projectId,
                      @Param("pluginCode") String pluginCode,
                      @Param("enabled") Boolean enabled,
                      @Param("configJson") String configJson);

    @Update("""
            UPDATE plugin_project_installs i
            SET enabled = FALSE,
                updated_at = CURRENT_TIMESTAMP(3)
            FROM plugin_catalog c
            WHERE c.plugin_id = i.plugin_id
              AND i.project_id = #{projectId}
              AND lower(c.code) = lower(#{pluginCode})
            """)
    int deleteInstall(@Param("projectId") Long projectId, @Param("pluginCode") String pluginCode);

    @Select("""
            SELECT id, plugin_call_log_id, project_id, run_id AS runId, plugin_code, tool_name,
                   CAST(request_json AS TEXT) AS request_json,
                   CAST(response_json AS TEXT) AS response_json,
                   latency_ms, status, error_msg, created_at
            FROM plugin_call_logs
            WHERE project_id = #{projectId}
            ORDER BY id DESC
            LIMIT 100
            """)
    List<PluginCallLog> listCallLogs(@Param("projectId") Long projectId);

    @Insert("""
            INSERT INTO plugin_call_logs(plugin_call_log_id, project_id, run_id, plugin_code, tool_name, request_json, response_json, latency_ms, status, error_msg)
            VALUES(#{pluginCallLogId}, #{projectId}, #{runId}, #{pluginCode}, #{toolName}, #{requestJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler}, #{responseJson,typeHandler=com.penmate.backend.infrastructure.persistence.support.JsonbTypeHandler}, #{latencyMs}, #{status}, #{errorMsg})
            """)
    int insertCallLog(PluginCallLog callLog);
}

