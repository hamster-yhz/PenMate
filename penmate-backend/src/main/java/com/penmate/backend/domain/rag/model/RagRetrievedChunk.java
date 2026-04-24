package com.penmate.backend.domain.rag.model;

import lombok.Data;

@Data
/**
 * RAG 检索命中片段实体。
 */
public class RagRetrievedChunk {
    /** 来源文档业务 ID。 */
    private Long documentId;
    /** 来源文档标题。 */
    private String documentTitle;
    /** 片段序号。 */
    private Integer chunkNo;
    /** 片段正文内容。 */
    private String contentText;
}

