package com.penmate.backend.application.novel.export;

public interface NovelDocumentRenderer {

    byte[] render(NovelExportFormat format, NovelManuscript manuscript);
}
