package com.penmate.backend.application.rag.command;

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

