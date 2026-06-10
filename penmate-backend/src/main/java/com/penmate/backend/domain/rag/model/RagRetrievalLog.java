package com.penmate.backend.domain.rag.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
/**
 * RAG 检索日志实体。
 */
public class RagRetrievalLog {
    /** 数据库物理主键 ID。 */
    private Long id;
    /** 检索日志业务 ID。 */
    private Long retrievalLogId;
    /** 所属项目业务 ID。 */
    private Long projectId;
    /** 关联生成任务业务 ID。 */
    private Long runId;
    /** 检索查询文本。 */
    private String queryText;
    /** 命中片段数量。 */
    private Integer hitCount;
    /** 命中来源明细（JSON）。 */
    private String sourcesJson;
    /** 检索耗时（毫秒）。 */
    private Integer latencyMs;
    /** 检索结果是否被采纳。 */
    private Boolean adopted;
    /** 全链路追踪 ID。 */
    private String traceId;
    /** 创建时间。 */
    private LocalDateTime createdAt;
}

