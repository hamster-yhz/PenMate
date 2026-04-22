package com.penmate.backend.domain.rag.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
/**
 * RAG 文档实体。
 */
public class RagDocument {
    /** 文档主键 ID。 */
    private Long id;
    /** 所属项目 ID。 */
    private Long projectId;
    /** 文档类型。 */
    private String docType;
    /** 文档标题。 */
    private String title;
    /** 来源引用（URL/路径/外部标识）。 */
    private String sourceRef;
    /** 原始文件对象键。 */
    private String originObjectKey;
    /** 原始文件 ETag。 */
    private String originEtag;
    /** MIME 类型。 */
    private String mimeType;
    /** 解析状态。 */
    private String parseStatus;
    /** 索引状态。 */
    private String indexStatus;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;

}

