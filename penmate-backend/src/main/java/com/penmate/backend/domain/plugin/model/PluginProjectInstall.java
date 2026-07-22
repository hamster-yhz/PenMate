package com.penmate.backend.domain.plugin.model;

import lombok.Data;
import java.time.Instant;

@Data
/**
 * 项目插件安装记录实体。
 */
public class PluginProjectInstall {
    /** 数据库物理主键 ID。 */
    private Long id;
    /** 安装记录业务 ID。 */
    private Long pluginInstallId;
    /** 所属项目业务 ID。 */
    private Long projectId;
    /** 插件业务 ID。 */
    private Long pluginId;
    /** 插件编码。 */
    private String pluginCode;
    /** 插件名称。 */
    private String pluginName;
    /** 已安装版本。 */
    private String version;
    /** 插件配置（JSON）。 */
    private String configJson;
    /** 是否启用。 */
    private Boolean enabled;
    /** 安装人用户业务 ID。 */
    private Long installedBy;
    /** 安装时间。 */
    private Instant installedAt;
    /** 最近更新时间。 */
    private Instant updatedAt;

}

