package com.penmate.backend.domain.plugin.model;

import lombok.Data;
import java.time.Instant;

@Data
/**
 * 插件调用日志实体。
 */
public class PluginCallLog {
    /** 数据库物理主键 ID。 */
    private Long id;
    /** 插件调用日志业务 ID。 */
    private Long pluginCallLogId;
    /** 所属项目业务 ID。 */
    private Long projectId;
    /** 关联生成任务业务 ID。 */
    private Long runId;
    /** 插件编码。 */
    private String pluginCode;
    /** 调用的工具名称。 */
    private String toolName;
    /** 调用请求报文（JSON）。 */
    private String requestJson;
    /** 调用响应报文（JSON）。 */
    private String responseJson;
    /** 调用耗时（毫秒）。 */
    private Integer latencyMs;
    /** 调用状态。 */
    private String status;
    /** 失败错误信息。 */
    private String errorMsg;
    /** 日志创建时间。 */
    private Instant createdAt;

}

