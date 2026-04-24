package com.penmate.backend.domain.plugin.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
/**
 * 插件目录项实体。
 */
public class PluginCatalogItem {
    /** 数据库物理主键 ID。 */
    private Long id;
    /** 插件业务 ID。 */
    private Long pluginId;
    /** 插件编码。 */
    private String code;
    /** 插件名称。 */
    private String name;
    /** 插件分类。 */
    private String category;
    /** 提供商标识。 */
    private String provider;
    /** 插件状态。 */
    private String status;
    /** 最新版本号。 */
    private String latestVersion;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;

}

