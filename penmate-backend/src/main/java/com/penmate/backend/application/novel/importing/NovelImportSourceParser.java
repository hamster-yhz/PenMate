package com.penmate.backend.application.novel.importing;

import com.penmate.backend.domain.novel.importing.NovelImportDraft;
import com.penmate.backend.domain.novel.importing.NovelImportFormat;

import java.io.IOException;
import java.io.InputStream;

public interface NovelImportSourceParser {
    NovelImportFormat format();

    NovelImportDraft parse(String filename, InputStream input) throws IOException;
}
