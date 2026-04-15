package com.penmate.backend.application.rag.command;

/**
 * CreateRagDocumentCommand。
 * <p>业务层：负责业务流程编排、领域对象协作与审计事件触发。</p>
 */
public record CreateRagDocumentCommand(
        String docType,
        String title,
        String sourceRef,
        String originObjectKey,
        String originEtag,
        String mimeType,
        Long operatorId
) {
}

