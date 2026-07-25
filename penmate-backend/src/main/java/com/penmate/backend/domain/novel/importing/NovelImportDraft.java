package com.penmate.backend.domain.novel.importing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record NovelImportDraft(
        String projectTitle,
        NovelImportFormat sourceFormat,
        List<Volume> volumes,
        List<Diagnostic> diagnostics
) {
    public static final int MAX_VOLUMES = 100;
    public static final int MAX_CHAPTERS = 2000;
    public static final long MAX_CHARACTERS = 20_000_000;

    public NovelImportDraft {
        projectTitle = normalizeTitle(projectTitle, "Imported novel");
        sourceFormat = sourceFormat == null ? NovelImportFormat.TXT : sourceFormat;
        volumes = volumes == null ? List.of() : List.copyOf(volumes);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public NovelImportDraft withDiagnostics() {
        List<Diagnostic> found = new ArrayList<>();
        Set<String> volumeTitles = new HashSet<>();
        Set<String> chapterTitles = new HashSet<>();
        int chapterCount = 0;
        for (int volumeIndex = 0; volumeIndex < volumes.size(); volumeIndex++) {
            Volume volume = volumes.get(volumeIndex);
            if (!volumeTitles.add(volume.title())) {
                found.add(new Diagnostic("DUPLICATE_VOLUME_TITLE", Severity.WARNING,
                        "Duplicate volume title", volumeIndex, null));
            }
            if (volume.chapters().isEmpty()) {
                found.add(new Diagnostic("EMPTY_VOLUME", Severity.ERROR,
                        "Volume has no chapters", volumeIndex, null));
            }
            for (int chapterIndex = 0; chapterIndex < volume.chapters().size(); chapterIndex++) {
                Chapter chapter = volume.chapters().get(chapterIndex);
                chapterCount++;
                if (!chapterTitles.add(chapter.title())) {
                    found.add(new Diagnostic("DUPLICATE_CHAPTER_TITLE", Severity.WARNING,
                            "Duplicate chapter title", volumeIndex, chapterIndex));
                }
                if (chapter.content().isBlank()) {
                    found.add(new Diagnostic("EMPTY_CHAPTER", Severity.WARNING,
                            "Chapter has no content", volumeIndex, chapterIndex));
                }
                if (chapter.content().length() < 20 && !chapter.content().isBlank()) {
                    found.add(new Diagnostic("SHORT_CHAPTER", Severity.INFO,
                            "Chapter is unusually short; check the detected boundary", volumeIndex, chapterIndex));
                }
            }
        }
        if (volumes.size() > MAX_VOLUMES || chapterCount > MAX_CHAPTERS) {
            found.add(new Diagnostic("IMPORT_TOO_LARGE", Severity.ERROR,
                    "Import exceeds the volume or chapter limit", null, null));
        }
        return new NovelImportDraft(projectTitle, sourceFormat, volumes, found);
    }

    public int chapterCount() {
        return volumes.stream().mapToInt(volume -> volume.chapters().size()).sum();
    }

    public long characterCount() {
        return volumes.stream().flatMap(volume -> volume.chapters().stream())
                .mapToLong(chapter -> chapter.content().codePointCount(0, chapter.content().length())).sum();
    }

    public void validateForImport() {
        if (projectTitle.isBlank() || projectTitle.length() > 200) throw new IllegalArgumentException("Invalid project title");
        if (volumes.isEmpty() || volumes.size() > MAX_VOLUMES) throw new IllegalArgumentException("Import requires 1 to 100 volumes");
        if (chapterCount() == 0 || chapterCount() > MAX_CHAPTERS) throw new IllegalArgumentException("Import requires 1 to 2000 chapters");
        if (characterCount() > MAX_CHARACTERS) throw new IllegalArgumentException("Import contains more than 20 million characters");
        for (Volume volume : volumes) {
            if (volume.title().isBlank() || volume.title().length() > 200 || volume.chapters().isEmpty()) {
                throw new IllegalArgumentException("Every volume requires a valid title and at least one chapter");
            }
            for (Chapter chapter : volume.chapters()) {
                if (chapter.title().isBlank() || chapter.title().length() > 200) {
                    throw new IllegalArgumentException("Every chapter requires a valid title");
                }
            }
        }
    }

    public record Volume(String title, List<Chapter> chapters) {
        public Volume {
            title = normalizeTitle(title, "Untitled volume");
            chapters = chapters == null ? List.of() : List.copyOf(chapters);
        }
    }

    public record Chapter(String title, String content) {
        public Chapter {
            title = normalizeTitle(title, "Untitled chapter");
            content = content == null ? "" : normalizeNewlines(content).strip();
        }
    }

    public record Diagnostic(String code, Severity severity, String message,
                             Integer volumeIndex, Integer chapterIndex) { }

    public enum Severity { INFO, WARNING, ERROR }

    private static String normalizeTitle(String value, String fallback) {
        String normalized = value == null ? "" : value.strip();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private static String normalizeNewlines(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }
}
